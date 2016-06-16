#!/usr/bin/env bash

( kill -SIGSTOP $BASHPID; exec "$4" "${@: 5}" ) &
app_pid=$!
./powerapi-agent $1 $2 $3 &
agent_pid=$!
kill -SIGCONT $app_pid
wait $agent_pid

exit 0