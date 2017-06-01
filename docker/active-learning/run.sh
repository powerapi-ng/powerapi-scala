#!/usr/bin/env bash

sh -c 'echo -1 >/proc/sys/kernel/perf_event_paranoid'
modprobe msr

cd powerapi-learning && ./bin/active-learning "$@"

exit 0
