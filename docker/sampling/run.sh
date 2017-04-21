#!/usr/bin/env bash

sh -c 'echo -1 >/proc/sys/kernel/perf_event_paranoid'
modprobe msr

cd powerapi-sampling-cpu && ./bin/sampling-cpu "$@" > /dev/null

exit 0
