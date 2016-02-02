#!/bin/bash

rm -f freqmine-1.log

${1}/pkgs/apps/freqmine/inst/arm-linux.gcc/bin/freqmine ${1}/pkgs/apps/freqmine/inputs/webdocs_250k.dat 11000 &>freqmine-1.log &

sleep 20

ps -ef | grep inst/ | grep -v grep | awk '{print $2}' | xargs kill -9 &>/dev/null

exit 0
