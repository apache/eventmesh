#!/bin/bash
set -e
script_path="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
echo "[`date`] WARNING this script will PAUSE jvm for seconds."
pid=$(jcmd | grep ProxyStartup | cut -d" " -f1)
jstack $pid | tee $script_path/../logs/jstack.$(date +%m%d%H%M).$pid.log
