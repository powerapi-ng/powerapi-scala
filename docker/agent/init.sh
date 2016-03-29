#!/usr/bin/env bash

docker create -v /apps --name apps_binary alpine /bin/true 2>/dev/null

if [ $? -ne 0 ]; then
  echo "The docker volume for application binaries has already been created."
fi

exit 0
