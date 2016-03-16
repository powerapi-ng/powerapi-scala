## Docker version of PowerAPI

Lightweight docker images of PowerAPI packaged as docker images.

You have first to create a [data volume](https://docs.docker.com/engine/userguide/containers/dockervolumes/#creating-and-mounting-a-data-volume-container) to store the configuration files (`conf` directory) for PowerAPI.
It will include all parameters needed to PowerAPI for configuring its internal components.
The different parameters to set up are described inside the [Wiki](https://github.com/Spirals-Team/powerapi/wiki).

We assume that two data volumes `powerapi-cli-conf` and `powerapi-sampling-conf` are created on the host system.

### PowerAPI: CLI

This image is used to run automatically a various choices of software-defined power meters.

#### Usage

Show the help text:

```
docker run --rm --privileged --net=host --pid=host \
--volumes-from powerapi-cli-conf \
spirals/powerapi-cli
```

The `--privileged` option is used to get the root access to the host machine,
the `--net=host` option is mandatory to be able to use the PowerSPY bluetooth power meter inside a container,
and the `--pid=host` is required to be able to get an access to the running apps of the host machine.

A classic example with the `ProcFS` module can be:

powerapi.conf:

```
powerapi.cpu.tdp = 35
```

Associated docker command:

```
docker run --rm --privileged --net=host --pid=host \
--volumes-from powerapi-cli-conf \
spirals/powerapi-cli \
modules procfs-cpu-simple monitor --frequency 1000 --all --console
```

### PowerAPI: Sampling

This image is used to build a CPU power model.

#### Usage

Launch the sampling:

```
docker run --rm --privileged --net=host --pid=host \
--volumes-from powerapi-sampling-conf \
spirals/powerapi-sampling
```

Example of a sampling configuration file:

sampling.conf:

```
powerspy.mac = "00:0B:CE:07:1E:9B"

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
