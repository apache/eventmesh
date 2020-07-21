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


echo "Now removing crontab....."
crontab -l | grep -v broker_watchdog | grep -v namesrv_watchdog > tmp_crontab.txt
crontab  tmp_crontab.txt
rm -f tmp_crontab.txt
echo "Finish...."

case $1 in
    broker)

    pid=`ps ax | grep -i 'cn.webank.defibus.broker.DeFiBusBrokerStartup' |grep java | grep -v grep | awk '{print $1}'`
    if [ -z "$pid" ] ; then
            echo "No DeFiBusBroker running."
            exit -1;
    fi

    if [ "$2" != "-f" ] ; then
            echo "read from pid.file"
            pid=`cat pid.file`
    fi

    echo "The DeFiBusBroker(${pid}) is running..."
    kill ${pid}

    echo "Send shutdown request to DeFiBusBroker(${pid}) OK"

    # wait for broker to shutdown
    while ps -p ${pid} > /dev/null 2>&1; do sleep 1; echo "waiting broker process ${pid} to exit."; done;

    rm -rf pid.file

    echo "broker process exits."
    ;;
    namesrv)

    pid=`ps ax | grep -i 'cn.webank.defibus.namesrv.DeFiBusNameSrvStartup' |grep java | grep -v grep | awk '{print $1}'`
    if [ -z "$pid" ] ; then
            echo "No DeFiBusNameSrv running."
            exit -1;
    fi

    echo "The DeFiBusNameSrv(${pid}) is running..."

    kill ${pid}

    echo "Send shutdown request to DeFiBusNameSrv(${pid}) OK"
    ;;
    *)
    echo "Useage: `basename $0` (broker|namesrv)"
esac