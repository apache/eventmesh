# Quick start Instruction

### dependencies
```
64bit OS, Linux/Unix is recommended;
64bit JDK 1.8+;
Gradle at least 5.6;
```

### download and build with sources

download source code from [https://github.com/WeBankFinTech/EventMesh](https://github.com/WeBankFinTech/EventMesh)  
You will get 'EventMesh-master.zip'  
To setup an eventmesh, you need to deploy the following components at least: eventmesh-emesher.  

**Step 1.** prepare the rocketmq/defibus runtime

**Step 2.** build eventmesh-emesher
```$xslt
cd /*YOUR DEPLOY PATH*/EventMesh-master/eventmesh-emesher
gradle clean tar -x test
```
You will get **eventmesh-emesher_1.0.0.tar.gz** in directory /* YOUR DEPLOY PATH */EventMesh-master/eventmesh-emesher/dist
### Deployment

**Step 3.** deploy eventmesn-emesher
```$xslt
upload eventmesh-emesher_1.0.0.tar.gz
tar -zxvf eventmesh-emesher_1.0.0.tar.gz
cd conf
fill up proxy.properties
cd ../bin
sh start.sh
```
If you see "ProxyTCPServer[port=10000] started....", you setup emesher successfully.

###Run eventmesh demo
