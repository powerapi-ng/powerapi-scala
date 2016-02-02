#!/bin/bash

rm -f fluidanimate-1.log
rm -rf /tmp/parsec
mkdir /tmp/parsec

${1}/pkgs/apps/fluidanimate/inst/amd64-linux.gcc/bin/fluidanimate 1 500  ${1}/pkgs/apps/fluidanimate/inputs/in_500K.fluid /tmp/parsec/out.fluid &>fluidanimate-1.log &

sleep 20

ps -ef | grep inst/ | grep -v grep | awk '{print $2}' | xargs kill -9 &>/dev/null

exit 0
