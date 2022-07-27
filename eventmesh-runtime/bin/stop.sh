#!/bin/sh
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

#detect operating system.
OS=$(uname)

EVENTMESH_HOME=`cd $(dirname $0)/.. && pwd`

export EVENTMESH_HOME

function get_pid {
	local ppid=""
	if [ -f ${EVENTMESH_HOME}/bin/pid.file ]; then
		ppid=$(cat ${EVENTMESH_HOME}/bin/pid.file)
	else
		if [[ $OS =~ Msys ]]; then
			# There is a Bug on Msys that may not be able to kill the identified process
			ppid=`jps -v | grep -i "org.apache.eventmesh.runtime.boot.EventMeshStartup" | grep java | grep -v grep | awk -F ' ' {'print $1'}`
		elif [[ $OS =~ Darwin ]]; then
			# Known problem: grep Java may not be able to accurately identify Java processes
			ppid=$(/bin/ps -o user,pid,command | grep "java" | grep -i "org.apache.eventmesh.runtime.boot.EventMeshStartup" | grep -Ev "^root" |awk -F ' ' {'print $2'})
		else
			# It is required to identify the process as accurately as possible on Linux
			ppid=$(ps -C java -o user,pid,command --cols 99999 | grep -w $EVENTMESH_HOME | grep -i "org.apache.eventmesh.runtime.boot.EventMeshStartup" | grep -Ev "^root" |awk -F ' ' {'print $2'})
		fi
	fi
	echo "$ppid";
}

pid=$(get_pid)
if [ -z "$pid" ];then
	echo -e "No eventmesh running.."
	exit 0;
fi

kill ${pid}
echo "Send shutdown request to eventmesh(${pid}) OK"

[[ $OS =~ Msys ]] && PS_PARAM=" -W "
stop_timeout=60
for no in $(seq 1 $stop_timeout); do
	if ps $PS_PARAM -p "$pid" 2>&1 > /dev/null; then
		if [ $no -lt $stop_timeout ]; then
			echo "[$no] shutdown server ..."
			sleep 1
			continue
		fi

		echo "shutdown server timeout, kill process: $pid"
		kill -9 $pid; sleep 1; break;
		echo "`date +'%Y-%m-%-d %H:%M:%S'` , pid : [$pid] , error message : abnormal shutdown which can not be closed within 60s" > ../logs/shutdown.error
	else
		echo "shutdown server ok!"; break;
	fi
done

if [ -f "pid.file" ]; then
    rm pid.file
fi


