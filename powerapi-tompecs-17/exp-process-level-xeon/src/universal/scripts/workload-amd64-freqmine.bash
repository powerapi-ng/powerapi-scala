#!/bin/bash

mkdir -p /tmp/parsec

${1}/pkgs/apps/freqmine/inst/amd64-linux.gcc/bin/freqmine ${1}/pkgs/apps/freqmine/inputs/webdocs_250k.dat 11000 &>freqmine.log &

exit 0
