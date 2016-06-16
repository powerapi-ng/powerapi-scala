#!/usr/bin/env bash

exec "$4" "${@: 5}" &
sleep 5
./powerapi-agent $1 $2 $3 &
agent_pid=$!
wait $agent_pid
exit 0
