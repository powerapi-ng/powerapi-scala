CC=cc
CFLAGS=-c -Wall -O0
INCLUDE=-I/usr/local/include 
LDFLAGS=-L/usr/local/lib -lpfm -lprotobuf-c -L/usr/lib -lunwind -lunwind-generic -lunwind-ptrace -ldw
SOURCES=payload.pb-c.c perf_util.c powerapi-agent.c
OBJECTS=$(SOURCES:.c=.o)
EXECUTABLE=powerapi-agent

all: $(SOURCES) $(EXECUTABLE)
    
$(EXECUTABLE): $(OBJECTS)
	$(CC) $(OBJECTS) -o $@ $(LDFLAGS)

payload.pb-c.c: payload.proto
	protoc-c --c_out=. $<

powerapi-agent.o:
	$(CC) $(CFLAGS) -D NB_CORES=$(NB_CORES) -D UNHALTED_CYCLES_EVT=\"$(UNHALTED_CYCLES_EVT)\" -D UNHALTED_REF_CYCLES_EVT=\"$(UNHALTED_REF_CYCLES_EVT)\" $(INCLUDE) powerapi-agent.c -o $@

.c.o:
	$(CC) $(CFLAGS) $(INCLUDE) $< -o $@

clean:
	rm -f $(OBJECTS) $(EXECUTABLE)
	rm -rf payload.pb-c.c payload.pb-c.h
