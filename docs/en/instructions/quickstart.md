# Quick start Instruction

### dependencies
```
64bit OS, Linux/Unix is recommended;
64bit JDK 1.8+;
Gradle at least 5.6;
4g+ free disk for eventmesh-store server
```

### download and build with sources

download source code from [https://github.com/WeBankFinTech/EventMesh](https://github.com/WeBankFinTech/EventMesh)  
You will get 'EventMesh-master.zip'  
To setup an eventmesh, you need to deploy the following components at least: eventmesh-store,  eventmesh-emesher.  

**build eventmesh-store**  
The eventmesh-store takes DeFiBus for example at the follwing parts, other implements such as Rocketmq etc. is coming soon.  

```
unzip EventMesh-master.zip
cd /*YOUR DEPLOY PATH*/EventMesh-master/eventmesh-store
gradle clean dist tar -x test
```
You will get **eventmesh-store_1.0.0.tar.gz** in directory /* YOUR DEPLOY PATH */EventMesh-master/eventmesh-store/build

**build eventmesh-emesher**
```$xslt
cd /*YOUR DEPLOY PATH*/EventMesh-master/eventmesh-emesher
gradle clean tar -x test
```
You will get **eventmesh-emesher_1.0.0.tar.gz** in directory /* YOUR DEPLOY PATH */EventMesh-master/eventmesh-emesher/dist
### Deployment


**Step 1.** deploy eventmesh-store  
- deploy DeFiBusNamesrv  
```
upload eventmesh-store_1.0.0.tar.gz
tar -zxvf eventmesh-store_1.0.0.tar.gz
cd bin
sh runnamesrv.sh
```
If you see "Thre Name Server boot success" in ../logs/namesrv.log, you setup DeFiBus Namesrv successfully.

- deploy DeFiBusBroker
```
upload eventmesh-store_1.0.0.tar.gz
tar -zxvf eventmesh-store_1.0.0.tar.gz
cd conf
fill up broker.properties
cd ../bin
sh runbroker.sh
```
If you see "The broker \[YOUR-BROKER-NAME, IP:PORT\] boot success." in ../logs/broker.log, you setup eventmesh-store successfully.

**Step 2.** deploy eventmesn-emesher
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
