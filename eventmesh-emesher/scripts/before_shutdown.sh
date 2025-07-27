#!/bin/bash

source session.sh | awk '{print $1}' | awk -F '=' '{print $2}' | awk -F '|' '{print $1,$2}' | grep 2019 > tmp.txt

cat tmp.txt | while read line
do
        cmd="./client_manage.sh -s $line"
        $cmd
        sleep 10s
done

rm tmp.txt

./client_manage.sh -a
