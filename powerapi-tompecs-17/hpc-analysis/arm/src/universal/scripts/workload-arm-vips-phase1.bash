#!/bin/bash

rm -f vips-1.log
rm -rf /tmp/parsec
mkdir /tmp/parsec

${1}/pkgs/apps/vips/inst/arm-linux.gcc/bin/vips im_benchmark ${1}/pkgs/apps/vips/inputs/orion_18000x18000.v /tmp/parsec/output.v &>vips-1.log &

sleep 20

ps -ef | grep inst/ | grep -v grep | awk '{print $2}' | xargs kill -9 &>/dev/null

exit 0
