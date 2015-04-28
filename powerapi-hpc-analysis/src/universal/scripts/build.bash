#!/bin/bash

parsec_home='/home/powerapi/parsec-2.1'

rm -f build.log

tar xf ${parsec_home}/pkgs/apps/blackscholes/inputs/input_native.tar -C ${parsec_home}/pkgs/apps/blackscholes/inputs &>build.log
tar xf ${parsec_home}/pkgs/apps/bodytrack/inputs/input_native.tar -C ${parsec_home}/pkgs/apps/bodytrack/inputs &>build.log
tar xf ${parsec_home}/pkgs/apps/facesim/inputs/input_native.tar -C . &>build.log
tar xf ${parsec_home}/pkgs/apps/fluidanimate/inputs/input_native.tar -C ${parsec_home}/pkgs/apps/fluidanimate/inputs &>build.log
tar xf ${parsec_home}/pkgs/apps/freqmine/inputs/input_native.tar -C ${parsec_home}/pkgs/apps/freqmine/inputs &>build.log
tar xf ${parsec_home}/pkgs/apps/vips/inputs/input_native.tar -C ${parsec_home}/pkgs/apps/vips/inputs &>build.log
tar xf ${parsec_home}/pkgs/apps/x264/inputs/input_native.tar -C ${parsec_home}/pkgs/apps/x264/inputs &>build.log
