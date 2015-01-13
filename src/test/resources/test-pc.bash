#!/bin/bash

(
  echo $BASHPID; kill -s SIGSTOP $BASHPID;

  i=0

  while true;
  do
     i+=1
  done
)
