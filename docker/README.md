## Docker version of PowerAPI

Lightweight docker images of PowerAPI packaged as docker images.  
The PowerAPI configuration directory is located at `/powerapi/conf` for all images.  
Please refer to the [Wiki](https://github.com/Spirals-Team/powerapi/wiki) for more information about PowerAPI configuration.

### PowerAPI: CLI

This image is used to run a various choices of software-defined power meters.

#### Usage

The minimal command line to run PowerAPI (print the help message):

```
docker run --rm --privileged spirals/powerapi-cli
```

The `--privileged` flag gives all capabilities to the PowerAPI container. (required to get metrics about system usage for the various modules)


#### Example

##### Monitoring the machine using a PowerSpy power meter

Content of the `powerapi-cli.conf`, adapt the MAC address accordingly:

```
powerspy.mac = "00:0B:CE:XX:XX:XX"
```

Command to run to start the monitoring using the PowerSpy power meter:

```
docker run \
--rm --privileged --net=host \
-v $PWD/powerapi-cli.conf:/powerapi/conf/powerapi-cli.conf \
spirals/powerapi-cli \
modules powerspy monitor --frequency 1000 --console
```

The `--net=host` option is mandatory to be able to access the host bluetooth card inside a container.  
If you have problem to connect to the PowerSpy, please check your ability to pair with PowerSpy from the host machine using `bluetoothctl`.

##### Monitoring a process using the ProcFS module (Linux only)

Content of the `powerapi-cli.conf`, adapt the CPU TDP accordingly:

```
powerapi.cpu.tdp = 130
```

Command to run to start the monitoring of the `firefox` application and the `1234` PID using the ProcFS module:

```
docker run \
--rm --privileged --pid=host \
-v $PWD/powerapi-cli.conf:/powerapi/conf/powerapi-cli.conf \
spirals/powerapi-cli \
modules procfs-cpu-simple monitor --frequency 1000 --apps firefox --pids 1234 --console
```

The `--pid=host` flag is required to be able to monitor the processes running on the host machine.

##### Monitoring a Docker container using the ProcFS module (Linux only)

Content of the `powerapi-cli.conf`, adapt the CPU TDP accordingly:

```
powerapi.cpu.tdp = 130
```

Command to run to start the monitoring of the `stress` container using the ProcFS module:

```
docker run \
--rm --privileged --net=host --pid=host \
-v /sys:/sys -v /var/run/docker.sock:/var/run/docker.sock \
-v $PWD/powerapi-cli.conf:/powerapi/conf/powerapi-cli.conf \
spirals/powerapi-cli \
modules procfs-cpu-simple monitor --frequency 1000 --containers stress --console
```

Access of the hosts `/sys` is required to access the Docker containers cgroups inside the container.  
The Docker socket is used to resolve the name, short and full ID of the container and check its existence.

### PowerAPI: Sampling

This image is used to build a CPU power model used by the `libpfm` module of PowerAPI.

#### Usage

First you have to configure PowerAPI, create a `powerapi-sampling-cpu.conf` file and adapt accordingly the following settings:

```
powerspy.mac = "00:0B:CE:XX:XX:XX"

powerapi.cpu.topology = [
  { core = 0, indexes = [0, 4] }
  { core = 1, indexes = [1, 5] }
  { core = 2, indexes = [2, 6] }
  { core = 3, indexes = [3, 7] }
]

powerapi.sampling.dvfs = true
powerapi.sampling.turbo = true

powerapi.cycles-polynom-regression.cpu-base-frequency = 0.133
powerapi.cycles-polynom-regression.cpu-max-frequency = 2.66
powerapi.cycles-polynom-regression.unhalted-cycles-event = "CPU_CLK_UNHALTED:THREAD_P"
powerapi.cycles-polynom-regression.ref-cycles-event = "CPU_CLK_UNHALTED:REF_P"

interval = 1s
powerapi.actors.timeout = 15s
powerapi.sampling.interval = ${interval}
powerspy.interval = ${interval}
powerapi.sampling.steps = [100, 25]
powerapi.sampling.step-duration = 10
```

Launch the CPU sampling with the following command:

```
docker run \
--rm --privileged --net=host --pid=host \
-v $PWD/powerapi-sampling-cpu.conf:/powerapi/conf/powerapi-sampling-cpu.conf \
-v $PWD/powerapi-sampling-cpu-results:/powerapi/results \
spirals/powerapi-sampling-cpu
```

The results of the sampling will be in the `powerapi-sampling-cpu-results` folder of the host.  
The CPU power model will be located at the `powerapi-sampling-cpu-results/computing/libpfm-formula.conf ` path.
