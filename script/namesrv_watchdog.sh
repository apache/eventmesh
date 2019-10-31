
Result=$(ps -ef|grep NamesrvStartup|grep -v grep | awk '{print $2}')
if [ "" == "$Result" ]
then
	export LANG=zh_CN.utf8
	export LC_ALL=zh_CN.UTF-8
	export LC_CTYPE=en_US.UTF-8
	./runnamesrv.sh
fi
