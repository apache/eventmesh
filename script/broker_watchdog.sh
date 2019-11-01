if [ ! -f "pid.file" ]; then
        Result_pid="noPid"
else
        Result_pid=`cat pid.file`
fi


Result=$(ps -ef|grep DeFiBusBrokerStartup|grep -v grep|grep $Result_pid)
if [ "" == "$Result" ]
then
	export LANG=zh_CN.utf8
	export LC_ALL=zh_CN.UTF-8
	export LC_CTYPE=en_US.UTF-8
	./runbroker.sh
fi
