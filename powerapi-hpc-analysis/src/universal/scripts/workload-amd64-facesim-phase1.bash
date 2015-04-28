#!/bin/bash

rm -f facesim-1.log

${1}/pkgs/apps/facesim/inst/amd64-linux.gcc/bin/facesim -timing -threads 1 -lastframe 100 &>facesim-1.log &

sleep 20

ps -ef | grep inst/ | grep -v grep | awk '{print $2}' | xargs kill -9 &>/dev/null

exit 0
