#!/bin/sh

#detect operating system.
OS=$(uname -o)

EVENTMESH_HOME=`cd "./.." && pwd`

export EVENTMESH_HOME

function get_pid {
	local ppid=""
	if [ -f ${EVENTMESH_HOME}/bin/pid.file ]; then
		ppid=$(cat ${EVENTMESH_HOME}/bin/pid.file)
	else
		if [[ $OS =~ Msys ]]; then
			# 在Msys上存在可能无法kill识别出的进程的BUG
			ppid=`jps -v | grep -i "com.webank.runtime.boot.EventMeshStartup" | grep java | grep -v grep | awk -F ' ' {'print $1'}`
		elif [[ $OS =~ Darwin ]]; then
			# 已知问题：grep java 可能无法精确识别java进程
			ppid=$(/bin/ps -o user,pid,command | grep "java" | grep -i "com.webank.runtime.boot.EventMeshStartup" | grep -Ev "^root" |awk -F ' ' {'print $2'})
		else
			#在Linux服务器上要求尽可能精确识别进程
			ppid=$(ps -C java -o user,pid,command --cols 99999 | grep -w $EVENTMESH_HOME | grep -i "com.webank.runtime.boot.EventMeshStartup" | grep -Ev "^root" |awk -F ' ' {'print $2'})
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


