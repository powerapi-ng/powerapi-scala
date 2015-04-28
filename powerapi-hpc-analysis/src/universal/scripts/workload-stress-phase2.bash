#!/bin/bash

rm -f stress-2.log

stress -c 1 &>stress-2.log &

sleep 40

ps -ef | grep stress | grep -v grep | awk '{print $2}' | xargs kill -9 &>/dev/null

exit 0
