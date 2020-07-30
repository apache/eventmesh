#!/bin/bash
set -e
script_path="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd $script_path
while true; do
    ./connections.sh
    sleep 60s;
done
