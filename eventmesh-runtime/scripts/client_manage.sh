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
i_eg="sh client_manage.sh -i 127.0.0.1 24591"
s_eg="sh client_manage.sh -s 5319"
r_eg="sh client_manage.sh -r 9876 127.0.0.1 10000"
a_eg="sh client_manage.sh -a"
x_eg="sh client_manage.sh -x 127.0.0.1 24591 127.0.0.1 10000"
y_eg="sh client_manage.sh -y bq-bypass 127.0.0.1 10000"

function printEg() {
        echo "param error."
        echo "reject client by ip_port, eg : ${i_eg}"
        echo "reject all clients, eg : ${a_eg}"
        echo "reject clients by systemid, eg : ${s_eg}"
        echo "redirect client by systemid, eg : ${r_eg}"
        echo "redirect client by ip port, eg : ${x_eg}"
        echo "redirect client by path, eg : ${y_eg}"
}

#PORT=24591
#localIp=`ifconfig|grep "inet addr:"|grep -v "127.0.0.1"|cut -d: -f2|awk '{print $1}'`
ADDR="127.0.0.1:10106"
echo "localAddress : ${ADDR}"
#parse command line options
ARGS=`getopt -o ai:s:r: --long -n 'client_manage.sh' -- "$@"`
if [ $? != 0 ] ; then echo "Failed parsing options." >&2 ; exit 1 ; fi
eval set -- "$ARGS"

while true
do
        case "$3" in
                -a|--all)
                        msg=`curl "http://${ADDR}/clientManage/rejectAllClient"`;echo ${msg};break;;
                -i|--ipport)
                        CLIENT_IP=$4
                        CLIENT_PORT=$5
                        msg=`curl "http://${ADDR}/clientManage/rejectClientByIpPort?ip=${CLIENT_IP}&port=${CLIENT_PORT}"`;echo ${msg};break;;
                -s|--subsystem)
                        SUB_SYSTEM=$4
                        msg=`curl "http://${ADDR}/clientManage/rejectClientBySubSystem?subSystem=${SUB_SYSTEM}"`;echo ${msg};break;;
                -x|--redirectbyip)
                        CLIENT_IP=$4
                        CLIENT_PORT=$5
                        DEST_PROXY_IP=$6
                        DEST_PROXY_PORT=$7
                        msg=`curl "http://${ADDR}/clientManage/redirectClientByIpPort?ip=${CLIENT_IP}&port=${CLIENT_PORT}&destProxyIp=${DEST_PROXY_IP}&destProxyPort=${DEST_PROXY_PORT}"`;echo ${msg};break;;
                -y|--redirectbypath)
                        CLIENT_PATH=$4
                        DEST_PROXY_IP=$5
                        DEST_PROXY_PORT=$6
                        msg=`curl "http://${ADDR}/clientManage/redirectClientByPath?path=${CLIENT_PATH}&destProxyIp=${DEST_PROXY_IP}&destProxyPort=${DEST_PROXY_PORT}"`;echo ${msg};break;;
                -r|--redirect)
                        SUB_SYSTEM=$4
                        DEST_PROXY_IP=$5
                        DEST_PROXY_PORT=$6
                        msg=`curl "http://${ADDR}/clientManage/redirectClientBySubSystem?subSystem=${SUB_SYSTEM}&destProxyIp=${DEST_PROXY_IP}&destProxyPort=${DEST_PROXY_PORT}"`;echo ${msg};break;;
                --)
                        shift;
                        break;;
                *)
                        printEg;
                        exit 1;;
        esac
done
