#!/bin/bash

rm -f ${2}-2.log

java -jar ${1}/dacapo-9.12-bach.jar $2 -s large  &>${2}-2.log

ps -ef | grep "${2} -s large" | grep -v grep | awk '{print $2}' | xargs kill -9 &>/dev/null

exit 0
