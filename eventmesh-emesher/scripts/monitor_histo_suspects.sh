#!/bin/bash
set -e
script_path="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd $script_path
while true; do
    ./histo_suspects.sh
    sleep 60s;
done
