#!/bin/bash

export LD_LIBRARY_PATH=/usr/local/lib

/usr/local/bin/mpirun -np ${2} ${1}/NPB3.3-MPI/bin/${3} &>${3}.log &

exit 0
