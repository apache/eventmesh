#!/bin/sh


# Copyright (C) @2017 Webank Group Holding Limited
#
# Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
# in compliance with the License. You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software distributed under the License
# is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
# or implied. See the License for the specific language governing permissions and limitations under
# the License.
#

#===========================================================================================
# Java Environment Setting
#===========================================================================================

TMP_JAVA_HOME="/nemo/jdk1.8.0_152"

function is_java8 {
        local _java="$1"
        [[ -x "$_java" ]] || return 1
        [[ "$("$_java" -version 2>&1)" =~ 'java version "1.8' || "$("$_java" -version 2>&1)" =~ 'openjdk version "1.8' ]] || return 2
        return 0
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

echo "proxy use java location= "$JAVA

PROXY_HOME=`cd "./.." && pwd`

export PROXY_HOME

export PROXY_LOG_HOME=${PROXY_HOME}/logs

echo "PROXY_HOME : ${PROXY_HOME}, PROXY_LOG_HOME : ${PROXY_LOG_HOME}"

function make_logs_dir {
        if [ ! -e "${PROXY_LOG_HOME}" ]; then mkdir -p "${PROXY_LOG_HOME}"; fi
}

error_exit ()
{
    echo "ERROR: $1 !!"
    exit 1
}

export JAVA_HOME

#===========================================================================================
# JVM Configuration
#===========================================================================================
JAVA_OPT="${JAVA_OPT} -server -Xms256m -Xmx512m -Xmn256m -XX:SurvivorRatio=4"
JAVA_OPT="${JAVA_OPT} -Dlog4j.configurationFile=${PROXY_HOME}/conf/log4j2.xml"
JAVA_OPT="${JAVA_OPT} -Dproxy.log.home=${PROXY_LOG_HOME}"
JAVA_OPT="${JAVA_OPT} -DconfPath=${PROXY_HOME}/conf"
JAVA_OPT="${JAVA_OPT} -Dlog4j2.AsyncQueueFullPolicy=Discard"

make_logs_dir

echo "using jdk[$JAVA]" >> ${PROXY_LOG_HOME}/proxy-client.out

conf=$1
proxy=$2
topic=$3
packetsize=$4
threads=$5

SYNC_REQ_MAIN=cn.webank.emesher.client.sdkdemo.SyncRequestInstance
$JAVA $JAVA_OPT -classpath ${PROXY_HOME}/conf:${PROXY_HOME}/lib/*:${PROXY_HOME}/apps/* $SYNC_REQ_MAIN $conf $proxy $topic $packetsize $threads >> ${PROXY_LOG_HOME}/proxy-client.out 2>&1 &
exit 0
