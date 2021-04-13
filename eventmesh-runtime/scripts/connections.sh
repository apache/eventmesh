#!/bin/bash
set -e
date;
echo -e "active eventmesh connections:\n$(netstat -lnap |grep EST  | grep $(jps | grep EventMeshStartup | cut -d" " -f1) | awk -F"[\t ]+" '{print $4}' |sort| uniq -c | sort -rnk1 | grep ":10000")"
echo -e "active  mq  connections:\n$(netstat -lnap |grep EST  | grep $(jps | grep EventMeshStartup | cut -d" " -f1) | awk -F"[\t ]+" '{print $5}' |sort | uniq -c | sort -rnk1 |grep -E "10911|9876")"
