#!/bin/bash
if [ $# -eq 0 ]
then
        curl -s 'http://127.0.0.1:10106/clientManage/showClient?'
elif [ $# -eq 1 ]
then
        TOPIC=$1
        curl -s "http://127.0.0.1:10106/clientManage/showListenClientByTopic?topic=${TOPIC}"
else
        CLIENT_DCN=$1
        CLIENT_SYSTEM=$2
        curl -s "http://127.0.0.1:10106/clientManage/showClientBySystemAndDcn?dcn=${CLIENT_DCN}&subSystem=${CLIENT_SYSTEM}"
fi
