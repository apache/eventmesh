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

#===========================================================================================
# Java Environment Setting
#===========================================================================================
set -e
# The configuration of different servers may be inconsistent,
# adding these configurations can avoid the problem of garbled characters
export LANG=en_US.UTF-8
export LC_CTYPE=en_US.UTF-8
export LC_ALL=en_US.UTF-8

TMP_JAVA_HOME="/nemo/jdk1.8.0_152"

#detect operating system.
OS=$(uname)

function is_java8 {
        local _java="$1"
        [[ -x "$_java" ]] || return 1
        [[ "$("$_java" -version 2>&1)" =~ 'java version "1.8' || "$("$_java" -version 2>&1)" =~ 'openjdk version "1.8' ]] || return 2
        return 0
}


function get_pid {
	local ppid=""
	if [ -f ${DEMO_HOME}/bin/pid_tcp_sub.file ]; then
		ppid=$(cat ${DEMO_HOME}/bin/pid_tcp_sub.file)
	else
		if [[ $OS =~ Msys ]]; then
			# There is a bug on Msys that may not be able to kill the recognized process
			ppid=`jps -v | grep -i "org.apache.eventmesh.tcp.demo.AsyncSubscribe" | grep java | grep -v grep | awk -F ' ' {'print $1'}`
		elif [[ $OS =~ Darwin ]]; then
			# Known issue: grep "java" may not be able to accurately identify the java process
			ppid=$(/bin/ps -o user,pid,command | grep "java" | grep -i "org.apache.eventmesh.tcp.demo.AsyncSubscribe" | grep -Ev "^root" |awk -F ' ' {'print $2'})
		else
			# It is required to identify the process as accurately as possible on the Linux server
			ppid=$(ps -C java -o user,pid,command --cols 99999 | grep -w $DEMO_HOME | grep -i "org.apache.eventmesh.tcp.demo.AsyncSubscribe" | grep -Ev "^root" |awk -F ' ' {'print $2'})
		fi
	fi
	echo "$ppid";
}


if [[ -d "$TMP_JAVA_HOME" ]] && is_java8 "$TMP_JAVA_HOME/bin/java"; then
        JAVA="$TMP_JAVA_HOME/bin/java"
elif [[ -d "$JAVA_HOME" ]] && is_java8 "$JAVA_HOME/bin/java"; then
        JAVA="$JAVA_HOME/bin/java"
elif  is_java8 "/nemo/jdk8/bin/java"; then
    JAVA="/nemo/jdk8/bin/java";
elif  is_java8 "/nemo/jdk1.8/bin/java"; then
    JAVA="/nemo/jdk1.8/bin/java";
elif  is_java8 "/nemo/jdk/bin/java"; then
    JAVA="/nemo/jdk/bin/java";
elif is_java8 "$(which java)"; then
        JAVA="$(which java)"
else
        echo -e "ERROR\t java(1.8) not found, operation abort."
        exit 9;
fi

echo "tcp_sub_demo use java location= "$JAVA

DEMO_HOME=`cd "./.." && pwd`

export DEMO_HOME

export DEMO_LOG_HOME=${DEMO_HOME}/logs

echo "DEMO_HOME : ${DEMO_HOME}, DEMO_LOG_HOME : ${DEMO_LOG_HOME}"

function make_logs_dir {
        if [ ! -e "${DEMO_LOG_HOME}" ]; then mkdir -p "${DEMO_LOG_HOME}"; fi
}

error_exit ()
{
    echo "ERROR: $1 !!"
    exit 1
}

export JAVA_HOME

JAVA_OPT=`cat ${DEMO_HOME}/conf/server.env | grep APP_START_JVM_OPTION::: | awk -F ':::' {'print $2'}`
JAVA_OPT="${JAVA_OPT} -XX:+UseG1GC -XX:G1HeapRegionSize=16m -XX:G1ReservePercent=25 -XX:InitiatingHeapOccupancyPercent=30 -XX:SoftRefLRUPolicyMSPerMB=0 -XX:SurvivorRatio=8 -XX:MaxGCPauseMillis=50"
JAVA_OPT="${JAVA_OPT} -verbose:gc -Xloggc:${DEMO_HOME}/logs/demo_tcp_sub_gc_%p.log -XX:+PrintGCDetails -XX:+PrintGCDateStamps -XX:+PrintGCApplicationStoppedTime -XX:+PrintAdaptiveSizePolicy"
JAVA_OPT="${JAVA_OPT} -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=${DEMO_HOME}/logs -XX:ErrorFile=${DEMO_HOME}/logs/hs_err_%p.log"
JAVA_OPT="${JAVA_OPT} -XX:+UseGCLogFileRotation -XX:NumberOfGCLogFiles=5 -XX:GCLogFileSize=30m"
JAVA_OPT="${JAVA_OPT} -XX:-OmitStackTraceInFastThrow"
JAVA_OPT="${JAVA_OPT} -XX:+AlwaysPreTouch"
JAVA_OPT="${JAVA_OPT} -XX:MaxDirectMemorySize=8G"
JAVA_OPT="${JAVA_OPT} -XX:-UseLargePages -XX:-UseBiasedLocking"
JAVA_OPT="${JAVA_OPT} -Dio.netty.leakDetectionLevel=advanced"
JAVA_OPT="${JAVA_OPT} -Dio.netty.allocator.type=pooled"
JAVA_OPT="${JAVA_OPT} -Djava.security.egd=file:/dev/./urandom"
JAVA_OPT="${JAVA_OPT} -Dlog4j.configurationFile=${DEMO_HOME}/conf/log4j2.xml"
#JAVA_OPT="${JAVA_OPT} -Deventmesh.log.home=${DEMO_LOG_HOME}"
JAVA_OPT="${JAVA_OPT} -DconfPath=${DEMO_HOME}/conf"
JAVA_OPT="${JAVA_OPT} -Dlog4j2.AsyncQueueFullPolicy=Discard"
JAVA_OPT="${JAVA_OPT} -Drocketmq.client.logUseSlf4j=true"

pid=$(get_pid)
if [ -n "$pid" ];then
	echo -e "ERROR\t the server is already running (pid=$pid), there is no need to execute start.sh again."
	exit 9;
fi

make_logs_dir

echo "using jdk[$JAVA]" >> ${DEMO_LOG_HOME}/demo_tcp_sub.out


DEMO_MAIN=org.apache.eventmesh.tcp.demo.sub.eventmeshmessage.AsyncSubscribe
if [ $DOCKER ]
then
	$JAVA $JAVA_OPT -classpath ${DEMO_HOME}/conf:${DEMO_HOME}/apps/*:${DEMO_HOME}/lib/* $DEMO_MAIN >> ${DEMO_LOG_HOME}/demo_tcp_sub.out
else
	$JAVA $JAVA_OPT -classpath ${DEMO_HOME}/conf:${DEMO_HOME}/apps/*:${DEMO_HOME}/lib/* $DEMO_MAIN >> ${DEMO_LOG_HOME}/demo_tcp_sub.out 2>&1 &
echo $!>pid_tcp_sub.file
fi
exit 0
