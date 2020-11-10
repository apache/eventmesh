# Eventmesh-store快速入门说明

### 依赖
```
建议使用64位操作系统，建议使用Linux / Unix；
64位JDK 1.8+;
Gradle至少为5.6, 推荐 5.6.*
4g +可用磁盘用于eventmesh-store服务器
```

### 下载源码
下载源代码[https://github.com/WeBankFinTech/DeFiBus](https://github.com/WeBankFinTech/DeFiBus)
您将获得**DefiBus-master.zip**

### 构建源码
eventmesh-store在下面的部分采用DeFiBus为例，因为默认情况下，eventmesh依赖于defibus作为存储层，其他工具如Rocketmq等也即将推出。
```
unzip DefiBus-master.zip
cd / *您的部署路径* / DefiBus-master
gradle clean dist tar -x test
```
您将在目录/*您的部署路径*/DefiBus-master/build中获得**DeFiBus_1.0.0.tar.gz**

### 部署
- 部署DeFiBusNamesrv
```
上传DeFiBus_1.0.0.tar.gz
tar -zxvf DeFiBus_1.0.0.tar.gz
cd bin
sh runnamesrv.sh
```
如果在../logs/namesrv.log中看到"Thre Name Server boot success”，则说明已成功设置DeFiBus Namesrv。

- 部署DeFiBusBroker
```
上传DeFiBus_1.0.0.tar.gz
tar -zxvf DeFiBus_1.0.0.tar.gz
cd conf
配置 broker.properties
cd ../bin
sh runbroker.sh
```
如果看到"The broker \[YOUR-BROKER-NAME, IP:PORT\] boot success."在../logs/broker.log中，
您可以成功设置eventmesh-store。