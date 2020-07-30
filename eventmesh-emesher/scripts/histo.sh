#!/bin/bash
set -e
script_path="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd $script_path
echo "[`date`] WARNING this script will PAUSE jvm for seconds."
pid=$(jcmd | grep ProxyStartup | cut -d" " -f1)
jmap -histo:live $pid | tee  $script_path/../logs/histo.$(date +%m%d%H%M).$pid.log
