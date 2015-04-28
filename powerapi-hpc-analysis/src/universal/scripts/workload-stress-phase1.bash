#!/bin/bash

rm -f stress-1.log

stress -c 1 &>stress-1.log &

sleep 20

ps -ef | grep stress | grep -v grep | awk '{print $2}' | xargs kill -9 &>/dev/null

exit 0
