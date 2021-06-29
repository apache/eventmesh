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
set -e;

function usage(){
	echo "Usage: watchdog.sh [option]";
	echo -e " -h, --help \t This help text."
	echo -e " -a, --add-crontab \t add watchdog task to crontab."
	echo -e " -d, --delete-crontab \t delete watchdog task from crontab."
	echo -e " -w, --work\t run the watchdog program for ONE time."
}

function add_crontab(){
	crontab -l | grep -v "$APP_HOME/bin/watchdog.sh" > tmp_crontab.txt || true
	mkdir -p $APP_HOME/logs/ && touch $APP_HOME/logs/watchdog.log
	echo "* * * * * $APP_HOME/bin/watchdog.sh -w >> $APP_HOME/logs/watchdog.log 2>&1" >> tmp_crontab.txt
	crontab tmp_crontab.txt
	rm -f tmp_crontab.txt
}

function delete_crontab(){
	crontab -l | grep -v "$APP_HOME/bin/watchdog.sh" > tmp_crontab.txt || true
	crontab tmp_crontab.txt
	rm -f tmp_crontab.txt
}

function restart_service(){
    echo "$(date) INFO stopping service ..."
	./stop.sh || { local code=$?; echo -e "$(date) ERROR\t failed to call stop.sh, code=$code."; exit $code; }

    echo "$(date) INFO starting service ..."
	./start.sh || { local code=$?; echo -e "$(date) ERROR\t failed to call start.sh, code=$code."; exit $code; }
	echo "$(date) INFO service restarted."
}

function work (){
	if [ ! -f "sys.pid" ]; then
	echo -e "$(date) ERROR\t sys.pid file not found, try to restart service."
		restart_service;
		exit $?;
	fi

	pid=$(cat sys.pid)
	if ps -fp ${pid} 2>&1 > /dev/null; then
		exit 0;
	else
		echo -e "$(date) ERROR\t process($pid) not found, try to restart service."
		restart_service;
		exit $?;
	fi
}

## script starts here.
APP_BIN="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
APP_HOME="$(dirname $APP_BIN)"; [ -d "$APP_HOME" ] || { echo "ERROR Mumble SDK Internal Bug, failed to detect APP_HOME."; exit 1;}
# parse command line.
cd ${APP_BIN};
OPTS=`getopt -o a::d::h::w:: --long add-crontab::,delete-crontab::,help::,work::  -- "$@"`
if [ $? != 0 ] ; then usage; exit 1 ; fi
eval set -- "$OPTS"
while true ; do
        case "$1" in
                -a|--add-crontab) add_crontab; exit $?;;
                -d|--delete-crontab) delete_crontab; exit $?;;
                -w|--work) work; exit $?;;
                -h|--help) usage; exit 0;;
                *) usage; exit 1 ;;
        esac
done
