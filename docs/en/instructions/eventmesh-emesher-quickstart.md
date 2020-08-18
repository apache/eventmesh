# Eventmesh-emesher Quick start Instruction

### dependencies
```
64bit OS, Linux/Unix is recommended;
64bit JDK 1.8+;
Gradle at least 5.6;
```

### download sources
download source code from [https://github.com/WeBankFinTech/EventMesh](https://github.com/WeBankFinTech/EventMesh)  
You will get **EventMesh-master.zip**
  
### build sources
```$xslt
unzip EventMesh-master.zip
cd /*YOUR DEPLOY PATH*/EventMesh-master/eventmesh-emesher
gradle clean tar -x test
```
You will get **eventmesh-emesher_1.0.0.tar.gz** in directory /* YOUR DEPLOY PATH */EventMesh-master/eventmesh-emesher/dist

### Deployment
- deploy eventmesn-emesher  
```$xslt
upload eventmesh-emesher_1.0.0.tar.gz
tar -zxvf eventmesh-emesher_1.0.0.tar.gz
cd conf
config your proxy.properties
cd ../bin
sh start.sh
```
If you see "ProxyTCPServer[port=10000] started....", you setup emesher successfully.

