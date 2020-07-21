#!/bin/sh
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.


#===========================================================================================
# Java Environment Setting
#===========================================================================================

TMP_JAVA_HOME="/nemo/jdk8"

function is_java8 {
        local _java="$1"
        [[ -x "$_java" ]] || return 1
        [[ "$("$_java" -version 2>&1)" =~ 'java version "1.8' ]] || return 2
        return 0
}

#0(not running),  1(is running)
function is_brokerRunning {
        local _pid="$1"
        local pid=`ps ax | grep -i 'cn.webank.defibus.broker.DeFiBusBrokerStartup' |grep java | grep -v grep | awk '{print $1}'|grep $_pid`
        if [ -z "$pid" ] ; then
            return 0
        else
            return 1
        fi
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

echo "broker use java location= "$JAVA

ROCKETMQ_HOME=`cd "./.." && pwd`

error_exit ()
{
    echo "ERROR: $1 !!"
    exit 1
}

export ROCKETMQ_HOME
export JAVA_HOME
#export JAVA="$JAVA_HOME/bin/java"
export BASE_DIR=$(dirname $0)/..
export CLASSPATH=.:${BASE_DIR}/conf:${CLASSPATH}

#===========================================================================================
# JVM Configuration
#===========================================================================================
JAVA_OPT="${JAVA_OPT} -server -Xms8g -Xmx8g -Xmn4g"
JAVA_OPT="${JAVA_OPT} -XX:+UseG1GC -XX:G1HeapRegionSize=16m -XX:G1ReservePercent=25 -XX:InitiatingHeapOccupancyPercent=30 -XX:SoftRefLRUPolicyMSPerMB=0 -XX:SurvivorRatio=8 -XX:MaxGCPauseMillis=50"
JAVA_OPT="${JAVA_OPT} -verbose:gc -Xloggc:/dev/shm/mq_gc_%p.log -XX:+PrintGCDetails -XX:+PrintGCDateStamps -XX:+PrintGCApplicationStoppedTime -XX:+PrintAdaptiveSizePolicy"
JAVA_OPT="${JAVA_OPT} -XX:+UseGCLogFileRotation -XX:NumberOfGCLogFiles=5 -XX:GCLogFileSize=30m"
JAVA_OPT="${JAVA_OPT} -XX:-OmitStackTraceInFastThrow"
JAVA_OPT="${JAVA_OPT} -XX:+AlwaysPreTouch"
JAVA_OPT="${JAVA_OPT} -XX:MaxDirectMemorySize=15g"
JAVA_OPT="${JAVA_OPT} -XX:-UseLargePages -XX:-UseBiasedLocking"
JAVA_OPT="${JAVA_OPT} -Djava.ext.dirs=${BASE_DIR}/lib:${BASE_DIR}/apps"
#JAVA_OPT="${JAVA_OPT} -Xdebug -Xrunjdwp:transport=dt_socket,address=9555,server=y,suspend=n"
JAVA_OPT="${JAVA_OPT} -cp ${CLASSPATH}"
JAVA_OPT="${JAVA_OPT} -Djava.security.egd=file:/dev/./urandom"

if [ -f "pid.file" ]; then
        pid=`cat pid.file`
        if ! is_brokerRunning "$pid"; then
            echo "broker is running already"
            exit 9;
        else
	    echo "err pid$pid, rm pid.file"
            rm pid.file
        fi
fi


nohup $JAVA ${JAVA_OPT} cn.webank.defibus.broker.DeFiBusBrokerStartup -c ../conf/broker.properties 2>&1 >/dev/null &
echo $!>pid.file
