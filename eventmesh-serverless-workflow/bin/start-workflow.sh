#!/bin/bash
#
# Licensed to Apache Software Foundation (ASF) under one or more contributor
# license agreements. See the NOTICE file distributed with
# this work for additional information regarding copyright
# ownership. Apache Software Foundation (ASF) licenses this file to you under
# the Apache License, Version 2.0 (the "License"); you may
# not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.

WORKFLOW_HOME=`cd $(dirname $0)/.. && pwd`
export WORKFLOW_HOME
export WORKFLOW_LOG_HOME=${WORKFLOW_HOME}/logs
echo "WORKFLOW_HOME : ${WORKFLOW_HOME}, WORKFLOW_LOG_HOME : ${WORKFLOW_LOG_HOME}"

serverLogFile=${WORKFLOW_HOME}/logs/workflow.log
serverHistoryLogFile=${WORKFLOW_HOME}/workflowHistory.log
pidConf=${WORKFLOW_HOME}/bin/pid.conf
serverName="eventmesh-workflow"
startScriptName="start-workflow.sh"

function make_logs_dir {
    if [ ! -e "${WORKFLOW_LOG_HOME}" ]; then mkdir -p "${WORKFLOW_LOG_HOME}"; fi
}

make_logs_dir

#check process
num=`ps -ef |grep ${serverName} |grep -v grep|wc -l`
echo "`date` the num of process is $num"
if [ $num -gt 0 ];then
    echo "the process is exist now "
    exit 0
fi

#start process
nohup ./${serverName} 2>&1 | tee  $serverLogFile>>$serverHistoryLogFile 2>&1 &
sleep 3

num=`ps -ef | grep ${serverName} | grep -v grep | wc -l`
echo "the num of process after start is $num"
if [ $num -lt 1 ];then
    echo "the process is not exit after start "
    exit 9
fi

pid=`ps -ef |grep ${serverName} |grep -v grep|head -n 1 |awk '{print $2}'`
echo $pid>$pidConf
exit 0