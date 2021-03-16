#!/bin/bash
set -e
script_path="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd $script_path
echo "[`date`] WARNING this script will PAUSE jvm for seconds."
pid=$(jcmd | grep ProxyStartup | cut -d" " -f1)
./histo.sh | grep -E "RRResponseFurture|PushConsumer|PullMessage|RR|RunnableAdapter|instance|ProducerFactory|ClientInstance|Session" | tee $script_path/../logs/histo.suspects.$(date +%m%d%H%M).$pid.log
