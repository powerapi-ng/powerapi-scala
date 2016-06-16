#define _GNU_SOURCE 1
#include <sys/types.h>
#include <sys/wait.h>
#include <inttypes.h>
#include <stdio.h>
#include <stdlib.h>
#include <errno.h>
#include <unistd.h>
#include <string.h>
#include <signal.h>
#include <fcntl.h>
#include <err.h>
#include <locale.h>
#include <sys/ioctl.h>
#include <sys/mman.h>
#include <dirent.h>
#include <time.h>

#include "perf_util.h"

#include <elfutils/libdwfl.h>
#include <sys/ptrace.h>
#include <libunwind.h>
#include <libunwind-ptrace.h>

#include <sys/socket.h>
#include <sys/un.h>
#include <netinet/in.h>

#include "payload.pb-c.h"

#define MAX_THREADS 4096

typedef struct {
  void* __internal;
  int length;
} array;

typedef struct {
  int a;
  int b;
} tuple;

typedef struct {
  array fds;
  pid_t tid;
} perf_event_desc_t_ext ;

typedef struct {
  char *event;
  uint64_t value;  
} counter_field;

typedef struct {
  int fd;
  int core;
} socket_t;


typedef struct {
  pid_t tid;
  unw_addr_space_t as;
  struct UPT_info *ui;
} unw_addr_space_t_ext;

char **events_str;
int nb_cores = NB_CORES;
int threshold = 0;
char software_cmd[256] = "";

Dwfl* dwfl;
size_t pgsz = 0;
static int buffer_pages = 8;
static array sockets = { .__internal = NULL, .length = 0 };
static array events;
static array current_pids = { .__internal = NULL, .length = 0 };
static array current_threads = { .__internal = NULL, .length = 0 };
static array fds_desc = { .__internal = NULL, .length = 0 };
static array addr_spaces = { .__internal = NULL, .length = 0 };

int sleep_step_ms = 250;

static int cmp(const void * a, const void * b) {
 return (*(int*)a - *(int*)b);
}

void sleep_ms(int milliseconds) {
  struct timespec ts;
  ts.tv_sec = milliseconds / 1000;
  ts.tv_nsec = (milliseconds % 1000) * 1000000;
  nanosleep(&ts, NULL);
}

static void add_unw_space(unw_addr_space_t_ext addr_space) {
  addr_spaces.__internal = realloc(addr_spaces.__internal, (addr_spaces.length + 1) * sizeof(unw_addr_space_t_ext));
  ((unw_addr_space_t_ext*)addr_spaces.__internal)[addr_spaces.length] = addr_space;
  addr_spaces.length += 1;
}

static int tid2space(pid_t tid) {
  int i = 0;
  unw_addr_space_t_ext *addr_spaces_tmp = (unw_addr_space_t_ext*)addr_spaces.__internal;

  for (i = 0; i < addr_spaces.length; i++) {
    if (addr_spaces_tmp[i].tid == tid) break;
  }

  return i;
} 

static void del_unw_space(pid_t tid) {
  unw_addr_space_t_ext *addr_spaces_tmp = (unw_addr_space_t_ext*)addr_spaces.__internal;
  int i = tid2space(tid), j = 0;
  unw_destroy_addr_space(addr_spaces_tmp[i].as);
  _UPT_destroy(addr_spaces_tmp[i].ui);  

  for (j = i; j < addr_spaces.length - 1; j++) {
    addr_spaces_tmp[j] = addr_spaces_tmp[j + 1];
  }

  addr_spaces.__internal = realloc(addr_spaces.__internal, (addr_spaces.length - 1) * sizeof(unw_addr_space_t_ext));
  addr_spaces.length -= 1;
}

/**
  * Update all running pids for the underlying command.
  */
static void update_pids() {
  FILE * in;
  char buffer[512];
  char pgrep_cmd[512];
  int retry = 0;

  array pids = { .__internal = NULL, .length = 0 };
  snprintf(pgrep_cmd, sizeof(pgrep_cmd), "ps -C %s -o pid --no-header", software_cmd);

  while (pids.length == 0 && retry < 10) {
    if ((in = popen(pgrep_cmd, "r"))) {
      while (fgets(buffer, sizeof(buffer), in) != NULL) {
        pids.__internal = realloc(pids.__internal, (pids.length + 1) * sizeof(pid_t));
        ((pid_t*)pids.__internal)[pids.length] = atol(buffer);
        pids.length += 1;
      }

      pclose(in);

      if (pids.length == 0) {
        retry += 1;
        sleep_ms(sleep_step_ms);
      }
    }
  }

  if (pids.length > 0) {
    if (current_pids.__internal != NULL) free(current_pids.__internal);
    current_pids.__internal = malloc(sizeof(pids.__internal));
    memcpy((pid_t*)current_pids.__internal, (pid_t*)pids.__internal, pids.length * sizeof(pid_t));
    current_pids.length = pids.length;
    free(pids.__internal);
  }

  else {
    current_pids.__internal = NULL;
    current_pids.length = 0;
  }
}

/**
  * Get the current timestamp in nanoseconds.
  */
static uint64_t current_timestamp_ns() {
  struct timespec ts; 
  timespec_get(&ts, TIME_UTC);
  return (uint64_t)(ts.tv_sec * 1000000000L + ts.tv_nsec);
}

/**
  * Clean all resources needed by perf and libpfm.
  */
static void clean_perf_resources(perf_event_desc_t* desc) {
  close(desc->fd);
  if (desc->group_leader) munmap(desc->buf, (buffer_pages + 1) * pgsz);
  free(desc->name);
  free(desc->fstr);
}

/**
  * Allow to dynamically add a fd structure to a global variable.
  */
static void add_fd_desc(perf_event_desc_t_ext desc_ext) {
  fds_desc.__internal = realloc(fds_desc.__internal, (fds_desc.length + 1) * sizeof(perf_event_desc_t_ext));
  ((perf_event_desc_t_ext*)fds_desc.__internal)[fds_desc.length] = desc_ext;
  fds_desc.length += 1;
}

/**
  * Allow to dynamically erase a fd structure from a global variable.
  */
static void del_fd_desc(pid_t tid) {
  int i, j;
  perf_event_desc_t_ext *fds_desc_tmp = (perf_event_desc_t_ext*)fds_desc.__internal;

  for (i = 0; i < fds_desc.length; i++) {
    if (fds_desc_tmp[i].tid == tid) {
      perf_event_desc_t *fds_tmp = (perf_event_desc_t*)fds_desc_tmp[i].fds.__internal;

      for (j = 0; j < fds_desc_tmp[i].fds.length; j++) {
        clean_perf_resources(&fds_tmp[j]);
      }
      break;
    }
  }

  for (j = i; j < fds_desc.length - 1; j++) {
    fds_desc_tmp[j] = fds_desc_tmp[j + 1];
  }

  fds_desc.__internal = realloc(fds_desc.__internal, (fds_desc.length - 1) * sizeof(perf_event_desc_t_ext));
  fds_desc.length -= 1;
}

/**
  * Get the corresponding indexes of a fd in order to retrieve a fd structure next.
  */
static tuple fd2event(int fd) {
  perf_event_desc_t_ext *fds_desc_tmp = (perf_event_desc_t_ext*)fds_desc.__internal;
  int i, j;
  tuple ret = { .a = -1, .b = -1 };

  for (i = 0; i < fds_desc.length; i++) {
    perf_event_desc_t *fds_tmp = (perf_event_desc_t*)fds_desc_tmp[i].fds.__internal;
    
    for (j = 0; j < fds_desc_tmp[i].fds.length; j++) {
      if(fds_tmp[j].fd == fd) {
        ret.a = i;
        ret.b = j;
        break;
      }
    }
  }

  return ret;
}

/**
  * Get the corresponding event associated to an unique id.
  */
static int id2event(array fds, int id) {
  perf_event_desc_t *_fds = (perf_event_desc_t*)fds.__internal;
  int i, n = fds.length;

  for (i = 0; i < n ; i++) {
    if(_fds[i].id == id) return i;
  }

  return -1;
}

/**
  * Open all counters for a tid (one counter per core/tid) and store the information inside a global variable.
  */
static array open_counters(pid_t tid) {
  int ret, i, j;
  uint64_t *val;
  size_t sz;
  char **_events = (char**)events.__internal;
  char **all_events = calloc(1, nb_cores * events.length * sizeof(char*));
  array fds = { .__internal = NULL, .length = 0 };
  for(i = 0; i < nb_cores * events.length; i++) {
    all_events[i] = calloc(1, strlen(_events[i % events.length]) + 1);
    all_events[i][0] = '\0';
    strncat(all_events[i], _events[i % events.length], strlen(_events[i % events.length]));
  }

  ret = perf_setup_argv_events((const char **)all_events, ((perf_event_desc_t**)&(fds.__internal)), &fds.length);
  if (ret || (fds.length == 0)) exit(1);

  perf_event_desc_t *_fds = (perf_event_desc_t*)fds.__internal;

  for(i = 0; i < nb_cores; i++) {
    _fds[i * events.length].fd = -1;

    for (j = 0; j < events.length; j++) {
      _fds[i * events.length + j].hw.disabled = !j;
      _fds[i * events.length + j].cpu = i;

      if (!j) {
        _fds[i * events.length + j].group_leader = 1;
        _fds[i * events.length + j].hw.wakeup_events = 1;
        _fds[i * events.length + j].hw.sample_type = PERF_SAMPLE_IP|PERF_SAMPLE_READ|PERF_SAMPLE_PERIOD|PERF_SAMPLE_CPU|PERF_SAMPLE_TID;
        _fds[i * events.length + j].hw.sample_period = threshold;
        _fds[i * events.length + j].hw.read_format = PERF_FORMAT_GROUP|PERF_FORMAT_ID;
      }

      _fds[i * events.length + j].fd = perf_event_open(&_fds[i * events.length + j].hw, tid, i, _fds[i * events.length].fd, 0);
      if (_fds[i * events.length + j].fd == -1) errx(1, "cannot attach event %s", _fds[i * events.length + j].name);
    }

    sz = (1 + 2 * events.length) * sizeof(uint64_t);
    val = malloc(sz);
    if (!val) errx(1, "cannot allocated memory");

    if (_fds[i * events.length].fd == -1) errx(1, "cannot create event 0");
    ret = read(_fds[i * events.length].fd, val, sz);
    if (ret == -1) errx(1, "cannot read id %zu", sizeof(val));

    for (j = 0; j < events.length; j++) {
      _fds[i * events.length + j].id = val[2 * (j + 1)];
      //printf("%"PRIu64"  %s\n", _fds[i * events.length + j].id, _fds[i * events.length + j].name);
    }

    //printf("\n\n");

    _fds[i * events.length].buf = mmap(NULL, (buffer_pages + 1) * pgsz, PROT_READ|PROT_WRITE, MAP_SHARED, _fds[i * events.length].fd, 0);
    if (_fds[i * events.length].buf == MAP_FAILED) err(1, "cannot mmap buffer");

    _fds[i * events.length].pgmsk = (buffer_pages * pgsz) - 1;

    ret = fcntl(_fds[i * events.length].fd, F_SETFL, fcntl(_fds[i * events.length].fd, F_GETFL, 0) | O_ASYNC);
    if (ret == -1) errx(1, "cannot set ASYNC");

    ret = fcntl(_fds[i * events.length].fd, F_SETSIG, SIGIO);
    if (ret == -1) err(1, "cannot setsig");

    ret = fcntl(_fds[i * events.length].fd, F_SETOWN, getpid());
    if (ret == -1) err(1, "cannot setown");

    ret = ioctl(_fds[i * events.length].fd, PERF_EVENT_IOC_REFRESH, PERF_IOC_FLAG_GROUP);
    if (ret == -1) err(1, "cannot refresh");

    ret = ioctl(_fds[i * events.length].fd, PERF_EVENT_IOC_RESET, PERF_IOC_FLAG_GROUP);
    if (ret == -1) err(1, "cannot refresh");

    free(val);
  }

  for(i = 0; i < nb_cores * events.length; i++) {
    free(all_events[i]);
  }
  free(all_events);

  return fds;
}

/**
  * Utility function to get the difference between two arrays.
  */
static array get_diff_arrays(array array1, array array2) {
  int diff[256];
  int *arr1 = (int*)array1.__internal, *arr2 = (int*)array2.__internal;
  int n1 = array1.length, n2 = array2.length;
  int i = 0, j = 0, k = 0;
  array ret;

  while (i < n1 && j < n2) {
    if(arr1[i] == arr2[j]) {
      i++;
      j++;
    }
    else if(arr1[i] < arr2[j]) {
      diff[k] = arr1[i];
      i++;
      k++;
    }
    else if(arr1[i] > arr2[j]) j++;
  }
  
  while (i < n1) {
    diff[k] = arr1[i];
    i++;
    k++;
  }
  
  ret.__internal = malloc(sizeof(diff));
  memcpy(ret.__internal, diff, sizeof(diff));
  ret.length = k;

  return ret;
}

/**
  * Update all running threads, clean local and external data when a thread does not exist anymore, open all counters and update data for new threads otherwise.
  */
static void update_threads() {
  array threads = { .__internal = NULL, .length = 0 };
  int i;

  update_pids();

  for (i = 0; i < current_pids.length; i++) {
    char dirname[64];
    DIR *dir;
    struct dirent *entry;
    int value = -1;
    char dummy;
    pid_t pid = ((pid_t*)current_pids.__internal)[i];

    snprintf(dirname, sizeof dirname, "/proc/%ld/task/", (long)pid);
    dir = opendir(dirname);
 
    if (!dir) errx(1, "pid %i does not exist: %s", pid, strerror(errno));

    while ((entry = readdir(dir)) != NULL) {
      value = -1;
      if (sscanf(entry->d_name, "%d%c", &value, &dummy) != 1) continue;
      threads.__internal = realloc(threads.__internal, (threads.length + 1) * sizeof(pid_t));
      ((pid_t*)threads.__internal)[threads.length] = (pid_t)value;
      threads.length += 1;
    }
  
    if (dir) closedir(dir);
  }

  if (threads.length > 0) {
    qsort(threads.__internal, threads.length, sizeof(pid_t), cmp);
    array old_threads = get_diff_arrays(current_threads, threads);
    array new_threads = get_diff_arrays(threads, current_threads);
 
    for (i = 0; i < new_threads.length; i++) {
      perf_event_desc_t_ext desc;
      array fds = open_counters(((pid_t*)new_threads.__internal)[i]);
      desc.tid = ((pid_t*)new_threads.__internal)[i];
      desc.fds = fds;
      add_fd_desc(desc);
      unw_addr_space_t_ext addr_space;
      addr_space.tid = ((pid_t*)new_threads.__internal)[i];
      addr_space.ui = _UPT_create(((pid_t*)new_threads.__internal)[i]);
      addr_space.as = unw_create_addr_space(&_UPT_accessors, 0);
      add_unw_space(addr_space);
    }

    for (i = 0; i < old_threads.length; i++) {
      del_fd_desc(((pid_t*)old_threads.__internal)[i]);
      del_unw_space(((pid_t*)old_threads.__internal)[i]);
      Payload payload = PAYLOAD__INIT;
      void *buf;
      unsigned len;
      payload.core = (uint32_t) 0;
      payload.pid = (uint32_t) 0;
      payload.tid = (uint32_t) ((pid_t*)old_threads.__internal)[i];
      payload.timestamp = (uint64_t) 0;

      len = payload__get_packed_size(&payload);
      buf = malloc(len);
      payload__pack(&payload, buf);

      int32_t sz = htonl((uint32_t)len);
      send(((socket_t*)sockets.__internal)[0].fd, &sz, sizeof(uint32_t), 0);
      send(((socket_t*)sockets.__internal)[0].fd, buf, len, 0);

      free(buf);
    }
    
    if (current_threads.__internal != NULL) free(current_threads.__internal);
    current_threads.__internal = malloc(sizeof(threads.__internal));
    memcpy((pid_t*)current_threads.__internal, (pid_t*)threads.__internal, threads.length * sizeof(pid_t));
    current_threads.length = threads.length;
    free(threads.__internal);
    if (old_threads.__internal != NULL) free(old_threads.__internal);
    if (new_threads.__internal != NULL) free(new_threads.__internal);
  }

  else {
    if (current_threads.__internal != NULL) free(current_threads.__internal);
    current_threads.length = 0;
  }
}

/**
  * Use libdwarf to retrieve the function name pointed by a given adress (instruction pointer).
  */
static const char* ip_to_function_name(const void* ip, pid_t tid) {
  Dwarf_Addr addr;
  Dwfl_Module* module;
  const char* function_name;
  if(dwfl_linux_proc_report(dwfl, tid)) errx(1, "dwfl_linux_proc_report failed");

  addr = (uintptr_t)ip;
  module = dwfl_addrmodule(dwfl, addr);
  function_name = dwfl_module_addrname(module, addr);

  if (dwfl_report_end(dwfl, NULL, NULL)) errx(1, "dwfl_report_end failed");

  return function_name;
}

/**
  * Callback executed when an interruption is launched.
  * This callback get informations about hardware counters with perf/libpfm and use ptrace for remote stack unwinding.
  * It also uses protobluff to send data over unix domain socket.
  */ 
static void interrupt_handler(int n, siginfo_t *info, void *context) {
  uint64_t timestamp = current_timestamp_ns();
  struct perf_event_header ehdr;
  unw_cursor_t cursor;
  int ret, evt_id, length = 0;
  tuple indexes;
  perf_event_desc_t* hw;
  uint64_t nr, val64;
  uint32_t cpu = 0, pid = 0, tid = 0, val32 = 0;
  struct { uint64_t value, id; } grp;
  const char *event;
  counter_field fields[events.length];
   
  ret = ioctl(info->si_fd, PERF_EVENT_IOC_DISABLE, PERF_IOC_FLAG_GROUP);
  if (ret == -1) goto reset_fd; // errx(1, "cannot disabled");

  if (info->si_code < 0) goto reset_fd; //errx(1, "signal not generated by kernel");
  if (info->si_code != POLL_HUP) goto reset_fd; //errx(1, "signal not generated by SIGIO");
  
  indexes = fd2event(info->si_fd);
  if (indexes.a == -1 || indexes.b == -1) goto reset_fd;// errx(1, "no event associated with fd=%d", info->si_fd);
  
  array fds = ((perf_event_desc_t_ext*)fds_desc.__internal)[indexes.a].fds;
  perf_event_desc_t *_fds = (perf_event_desc_t*)fds.__internal;

  ret = perf_read_buffer(_fds + indexes.b, &ehdr, sizeof(ehdr));
  if (ret) goto reset_fd; //errx(1, "cannot read event header");

  if (ehdr.type != PERF_RECORD_SAMPLE) {
    warnx("unexpected sample type=%d, skipping\n", ehdr.type);
    perf_skip_buffer(_fds + indexes.b, ehdr.size);
    goto reset_fd;
  }
  
  hw = _fds + indexes.b;

  // PERF_SAMPLE_IP
  ret = perf_read_buffer_64(hw, &val64);
  if (ret) warnx("cannot read IP");

  // PERF_SAMPLE_TID
  ret = perf_read_buffer_32(hw, &pid);
  if (ret) warnx("cannot read PID");
  ret = perf_read_buffer_32(hw, &tid);
  if (ret) warnx("cannot read TID");
  
  ptrace(PTRACE_ATTACH, tid, 0, 0); 
  wait(NULL);

  // PERF_SAMPLE_CPU
  ret = perf_read_buffer_32(hw, &cpu);
  if (ret) warnx("cannot read CPU");
  ret = perf_read_buffer_32(hw, &val32);
  if (ret) warnx("cannot read reserved field CPU");

  // PERF_SAMPLE_PERIOD
  ret = perf_read_buffer_64(hw, &val64);
  if (ret) warnx("cannot read PERIOD");

  // PERF_SAMPLE_READ
  ret = perf_read_buffer_64(hw, &nr);
  if (ret) warnx("cannot read nr");

  while(nr--) {
    grp.id = -1;

    ret = perf_read_buffer_64(hw, &grp.value);
    if (ret) {warnx("cannot read group value"); }

    ret = perf_read_buffer_64(hw, &grp.id);
    if (ret) warnx("cannot read leader id");

    evt_id = id2event(fds, grp.id);
    if (evt_id == -1) event = "unknown";
    else event = _fds[evt_id].name;
    
    fields[nr].event = (char*)event;
    fields[nr].value = grp.value;

    //printf("\tPID: %"PRIu32", TID: %"PRIu32", CPU: %"PRIu32", TIMESTAMP: %"PRIu64"; %"PRIu64" %s (group: %"PRIu64")\n", pid, tid, cpu, timestamp, grp.value, event, grp.id);
  }
  
  //printf("\n\n");
  
  unw_addr_space_t_ext addr_space = ((unw_addr_space_t_ext*)(addr_spaces.__internal))[tid2space(tid)];
  ret = unw_init_remote(&cursor, addr_space.as, addr_space.ui);
  if (ret) goto detach_tid;

  char *strings[256];

  while(unw_step(&cursor) > 0) {
    unw_word_t ip;
    unw_get_reg(&cursor, UNW_REG_IP, &ip);
    if (ip == 0) break;
    char *name = (char*)ip_to_function_name((void*)ip, tid);
    // FIX: it sometimes appears that the name is set to the null character when an address is not reachable (why?)
    if (name == '\0') break;
    strings[length] = malloc((strlen(name) + 1) * sizeof(char));
    strings[length][0] = '\0';
    strncat(strings[length], name, strlen(name));
    length += 1;
    if (strcmp(name, "main") == 0) break;
  }

  if (length > 0) {
    int i = 0;
    Payload payload = PAYLOAD__INIT;
    void *buf;
    unsigned len;
    payload.core = (uint32_t) cpu;
    payload.pid = (uint32_t) pid;
    payload.tid = (uint32_t) tid;
    payload.timestamp = (uint64_t) timestamp;
    payload.n_counters = events.length;
    payload.n_traces = length;
    
    payload.counters = malloc(payload.n_counters * sizeof(MapEntry));
    payload.traces = malloc(payload.n_traces * sizeof(char*));

    for (i = 0; i < payload.n_counters; i++) {
      payload.counters[i] = malloc(sizeof(MapEntry));
      map_entry__init(payload.counters[i]);
      payload.counters[i]->key = fields[i].event;
      payload.counters[i]->value = (uint64_t)fields[i].value;
    }

    for (i = 0; i < payload.n_traces; i++) {
      payload.traces[i] = strings[i];
    }

    len = payload__get_packed_size(&payload);
    buf = malloc(len);
    payload__pack(&payload, buf);

    int32_t sz = htonl((uint32_t)len);
    send(((socket_t*)sockets.__internal)[cpu].fd, &sz, sizeof(uint32_t), 0);
    send(((socket_t*)sockets.__internal)[cpu].fd, buf, len, 0);
    
    free(buf);
    for (i = 0; i < length ; i++) free(strings[i]);
  }

detach_tid: 
  ptrace(PTRACE_DETACH, tid, 0, 0);
reset_fd:
  ret = ioctl(info->si_fd, PERF_EVENT_IOC_REFRESH, PERF_IOC_FLAG_GROUP);
  if (ret == -1) err(1, "cannot refresh");
  ret = ioctl(info->si_fd, PERF_EVENT_IOC_RESET, PERF_IOC_FLAG_GROUP);
  if (ret == -1) errx(1, "cannot reset");
  ret = ioctl(info->si_fd, PERF_EVENT_IOC_ENABLE, PERF_IOC_FLAG_GROUP);
  if (ret == -1) errx(1, "cannot enable");
}

/**
  * Main code of the PowerAPI's agent.
  * This agent is responsible to set the interruption mode on an external program and to get detailed information when such interruption occurs.
  * The stack trace and the associated hardware counters are retrieved and send to a PowerAPI which is running as a daemon.
  * One Unix server socket (a control socket) is created by PowerAPI for handling softwares and connexion, and server sockets are created per core by the Agent to send interruption data to PowerAPI.
  *
  * argv[1] => threshold
  * argv[2] => software's label
  * argv[3] => software's cmd used as a direct pgrep parameter.
  */
int main(int argc, char **argv) {
  struct sigaction act;
  sigset_t new, old;
  char *message;
  int ret, i, j;
  struct sockaddr_un addr;
  int socket_servers[nb_cores];
  int control_socket;
  socket_t _sockets[nb_cores];
  char software_label[256] = "";

  pgsz = sysconf(_SC_PAGESIZE);
  events_str = malloc(2 * sizeof(char*));
  events_str[0] = strdup(UNHALTED_CYCLES_EVT);
  events_str[1] = strdup(UNHALTED_REF_CYCLES_EVT);
  events.__internal = events_str;
  events.length = 2;

  threshold = atol(argv[1]);
  strcpy(software_label, argv[2]);
  strcpy(software_cmd, argv[3]);

  char *debuginfo_path = NULL;

  Dwfl_Callbacks callbacks = {
    .find_elf=dwfl_linux_proc_find_elf,
    .find_debuginfo=dwfl_standard_find_debuginfo,
    .debuginfo_path=&debuginfo_path,
  };

  dwfl = dwfl_begin(&callbacks);
 
  for (i = 0; i < nb_cores; i++) {
    char socket_path[256];
    snprintf(socket_path, sizeof(socket_path), "/tmp/agent-%d-%s.sock", i, software_label);
    socket_servers[i] = socket(AF_UNIX, SOCK_STREAM, 0);
    if (socket_servers[i] == -1) errx(1, "Socket error %s", socket_path);

    memset(&addr, 0, sizeof(addr));
    addr.sun_family = AF_UNIX;
    strncpy(addr.sun_path, socket_path, sizeof(addr.sun_path) - 1);  

    unlink(socket_path);

    ret = bind(socket_servers[i], (struct sockaddr*)&addr, sizeof(addr));
    if (ret < 0) errx(1, "Bind error");

    ret = listen(socket_servers[i], 1);
    if (ret < 0) errx(1, "Listen error");
  }

  sockets.__internal = _sockets;
  sockets.length = nb_cores;

  control_socket = socket(AF_UNIX, SOCK_STREAM, 0);
  memset(&addr, 0, sizeof(addr));
  addr.sun_family = AF_UNIX;
  strncpy(addr.sun_path, "/tmp/agent-control.sock", sizeof(addr.sun_path) - 1);
  if (connect(control_socket, (struct sockaddr*)&addr, sizeof(addr)) == -1) errx(1, "Connect error");
  message = (char*)malloc(128 * sizeof(char));
  snprintf(message, 128, "%s\n", software_label);
  send(control_socket, message, strlen(message), 0);
  free(message);
  message = (char*)malloc(128 * sizeof(char));
  snprintf(message, 128, "%s\n", software_cmd);
  send(control_socket, message, strlen(message), 0);
  free(message);

  for (i = 0; i < nb_cores; i++) {
    socket_t sock;
    sock.fd = accept(socket_servers[i], NULL, NULL);
    if (sock.fd < 0) errx(1, "Accept error");
    sock.core = i;
    _sockets[i] = sock;
  }

  ret = pfm_initialize();
  if (ret != PFM_SUCCESS) errx(1, "Cannot initialize library: %s", pfm_strerror(ret));

  memset(&act, 0, sizeof(act));
  act.sa_sigaction = interrupt_handler;
  act.sa_flags = SA_SIGINFO;
  sigaction(SIGIO, &act, 0);

  sigemptyset(&old);
  sigemptyset(&new);
  sigaddset(&new, SIGIO);

  ret = sigprocmask(SIG_SETMASK, NULL, &old);
  if (ret) errx(1, "sigprocmask failed");

  if (sigismember(&old, SIGIO)) {
    warnx("program started with masked signal, unmasking it now\n");
    ret = sigprocmask(SIG_UNBLOCK, &new, NULL);
    if (ret) errx(1, "sigprocmask failed");
  }
  
  update_threads();

  while (current_threads.length > 0) {
    sleep_ms(sleep_step_ms);
    update_threads();
  }
 
  message = (char*)malloc(128 * sizeof(char));
  snprintf(message, 128, "END\n");
  send(control_socket, message, strlen(message), 0);
  free(message);

  free(dwfl);
  
  perf_event_desc_t_ext *fds_desc_tmp = (perf_event_desc_t_ext*)fds_desc.__internal; 
  for (i = 0; i < fds_desc.length; i++) {
    perf_event_desc_t *fds_tmp = (perf_event_desc_t*)fds_desc_tmp[i].fds.__internal;
    
    for (j = 0; j < fds_desc.length; j++) {
      clean_perf_resources(&fds_tmp[j]);
    }      
  } 
  
  if (fds_desc.__internal != NULL) free(fds_desc.__internal);
  if (addr_spaces.__internal != NULL) free(addr_spaces.__internal);
  if (current_pids.__internal != NULL) free(current_pids.__internal);
  if (current_threads.__internal != NULL) free(current_threads.__internal);

  for (i = 0; i < sockets.length; i++) {
    close(((socket_t*)sockets.__internal)[i].fd);
    close(socket_servers[i]);
    char socket_path[256];
    snprintf(socket_path, sizeof(socket_path), "/tmp/agent-%d-%s.sock", i, software_label);
    unlink(socket_path);
  }
  close(control_socket);

  for (i = 0; i < events.length; i++) {
    free(((char**)events.__internal)[i]);
  }
  free(events.__internal);

  pfm_terminate();
  
  return 0;
}

