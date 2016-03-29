#!/usr/bin/env bash

( kill -SIGSTOP $BASHPID; exec "$2" "${@: 3}" ) &
app_pid=$!

./powerapi-agent $1 $app_pid &
agent_pid=$!

sleep 15

kill -SIGCONT $app_pid

wait $agent_pid

exit 0
