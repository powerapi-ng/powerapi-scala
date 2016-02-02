#!/bin/bash

rm -f blackscholes-1.log
rm -rf /tmp/parsec
mkdir /tmp/parsec

${1}/pkgs/apps/blackscholes/inst/amd64-linux.gcc/bin/blackscholes 1 ${1}/pkgs/apps/blackscholes/inputs/in_10M.txt /tmp/parsec/prices.txt &>blackscholes-1.log &

sleep 20

ps -ef | grep inst/ | grep -v grep | awk '{print $2}' | xargs kill -9 &>/dev/null

exit 0
