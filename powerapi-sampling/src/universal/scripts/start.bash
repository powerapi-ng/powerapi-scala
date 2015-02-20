#!/bin/bash

(echo $BASHPID; kill -s SIGSTOP $BASHPID; exec $@)
