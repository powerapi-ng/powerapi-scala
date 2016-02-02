#!/bin/bash

rm -f blackscholes-2.log
rm -rf /tmp/parsec
mkdir /tmp/parsec

${1}/pkgs/apps/blackscholes/inst/arm-linux.gcc/bin/blackscholes 1 ${1}/pkgs/apps/blackscholes/inputs/in_10M.txt /tmp/parsec/prices.txt &>blackscholes-2.log

ps -ef | grep inst/ | grep -v grep | awk '{print $2}' | xargs kill -9 &>/dev/null

exit 0
