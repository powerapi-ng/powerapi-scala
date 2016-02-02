#!/bin/bash

rm -f x264-2.log
rm -rf /tmp/parsec
mkdir /tmp/parsec

${1}/pkgs/apps/x264/inst/arm-linux.gcc/bin/x264 --quiet --qp 20 --partitions b8x8,i4x4 --ref 5 --direct auto --b-pyramid --weightb --mixed-refs --no-fast-pskip --me umh --subme 7 --analyse b8x8,i4x4 --threads 1 -o /tmp/parsec/eledream.264 ${1}/pkgs/apps/x264/inputs/eledream_1920x1080_512.y4m &>x264-2.log

ps -ef | grep inst/ | grep -v grep | awk '{print $2}' | xargs kill -9 &>/dev/null

exit 0
