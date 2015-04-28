#!/bin/bash

rm -f facesim-2.log

${1}/pkgs/apps/facesim/inst/amd64-linux.gcc/bin/facesim -timing -threads 1 -lastframe 100 &>facesim-2.log

ps -ef | grep inst/ | grep -v grep | awk '{print $2}' | xargs kill -9 &>/dev/null

exit 0
