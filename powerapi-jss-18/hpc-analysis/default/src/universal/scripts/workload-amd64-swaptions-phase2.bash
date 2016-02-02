#!/bin/bash

rm -f swaptions-2.log

${1}/pkgs/apps/swaptions/inst/amd64-linux.gcc/bin/swaptions -ns 128 -sm 1000000 -nt 1 &>swaptions-2.log

ps -ef | grep inst/ | grep -v grep | awk '{print $2}' | xargs kill -9 &>/dev/null

exit 0
