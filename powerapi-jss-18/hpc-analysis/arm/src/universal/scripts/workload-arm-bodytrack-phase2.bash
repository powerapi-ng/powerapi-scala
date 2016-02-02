#!/bin/bash

rm -f bodytrack-2.log

${1}/pkgs/apps/bodytrack/inst/arm-linux.gcc/bin/bodytrack ${1}/pkgs/apps/bodytrack/inputs/sequenceB_261 4 261 4000 5 0 1 &>bodytrack-2.log

ps -ef | grep inst/ | grep -v grep | awk '{print $2}' | xargs kill -9 &>/dev/null

exit 0
