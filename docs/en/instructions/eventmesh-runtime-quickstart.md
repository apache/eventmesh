# Eventmesh-runtime Quick start Instruction

## 1 Remote Deploy

### 1.1 dependencies

```
64bit OS, Linux/Unix is recommended;
64bit JDK 1.8+;
Gradle at least 7.0, eg 7.0.*
```

### 1.2 download sources

download source code from [https://github.com/apache/incubator-eventmesh](https://github.com/apache/incubator-eventmesh)  
You will get **EventMesh-master.zip**

### 1.3 build sources

```$xslt
unzip EventMesh-master.zip
cd /*YOUR DEPLOY PATH*/EventMesh-master
gradle clean dist copyConnectorPlugin tar -x test
```

You will get **EventMesh_1.3.0-SNAPSHOT.tar.gz** in directory /* YOUR DEPLOY PATH */EventMesh-master/build

### 1.4 Deployment

- deploy eventmesh-runtime

```shell
tar -zxvf Eventmesh_1.3.0-SNAPSHOT.tar.gz
cd conf
config your eventMesh.properties
cd ../bin
sh start.sh
tail -f ./logs/eventmesh.out
EventMeshTCPServer[port=10000] started
...
HTTPServer[port=10105] started
...
```


## 2 Run Locally

### 2.1 dependencies

Same with 1.1, but it can be only compiled in JDK 1.8

### 2.2 download sources

Same with 1.2

### 2.3 Configuration

**2.3.1 Configure plugin**

Specify the connector plugin that will be loaded after the project start by declaring in `eventMesh.properties`

Modify the `eventMesh.properties` file in the `confPath` directory

```java
#connector plugin, default standalone, can be rocketmq
eventMesh.connector.plugin.type=standalone
```

**2.3.2 Configure VM Options**

```java
-Dlog4j.configurationFile=eventmesh-runtime/conf/log4j2.xml
-Deventmesh.log.home=eventmesh-runtime/logs
-Deventmesh.home=eventmesh-runtime/dist
-DconfPath=eventmesh-runtime/conf
```
> ps: If you use Windows, you may need to replace the file separator to \

**2.3.3 Run**
```java
running `org.apache.eventmesh.starter.StartUp` main method in eventmesh-starter module.

EventMeshTCPServer[port=10000] started
...
HTTPServer[port=10105] started
...
```