#!/usr/bin/env bash

cd powerapi-sampling-cpu && ./bin/sampling-cpu --all results/sampling results/processing results/computing

echo ""
echo "Here is your CPU power model to use with PowerAPI"
echo ""
cat results/computing/libpfm-formula.conf
echo ""
