#Eventmesh-runtime快速入门说明

###依赖
```
建议使用64位操作系统，建议使用Linux / Unix；
64位JDK 1.8+;
Gradle至少为5.6, 推荐 5.6.*
```

###下载源码
[https://github.com/WeBankFinTech/EventMesh](https://github.com/WeBankFinTech/EventMesh)
您将获得**EventMesh-master.zip**
  
###构建源码
```$ xslt
unzip EventMesh-master.zip
cd / *您的部署路径* /EventMesh-master/eventmesh-runtime
gradle clean tar -x test
```
您将在目录/ *您的部署路径* /EventMesh-master/eventmesh-runtime/dist中获得**eventmesh-runtime_1.0.0.tar.gz**

###部署
-部署eventmesh-runtime
```$ xslt
upload eventmesh-runtime_1.0.0.tar.gz
tar -zxvf eventmesh-runtime_1.0.0.tar.gz
cd bin
配置 proxy.properties
cd ../bin
sh start.sh
```
如果看到"ProxyTCPServer[port=10000] started...."，则说明设置成功。