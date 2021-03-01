# Eventmesh-runtime Quick start Instruction

## 1 Remote Deploy

### 1.1 dependencies

```
64bit OS, Linux/Unix is recommended;
64bit JDK 1.8+;
Gradle at least 5.6, eg 5.6.*
```

### 1.2 download sources
download source code from [https://github.com/WeBankFinTech/EventMesh](https://github.com/WeBankFinTech/EventMesh)  
You will get **EventMesh-master.zip**

### 1.3 build sources
```$xslt
unzip EventMesh-master.zip
cd /*YOUR DEPLOY PATH*/EventMesh-master/eventmesh-runtime
gradle clean tar -x test
```
You will get **eventmesh-runtime_1.0.0.tar.gz** in directory /* YOUR DEPLOY PATH */EventMesh-master/eventmesh-runtime/dist

### 1.4 Deployment
- deploy eventmesh-runtime  
```$xslt
upload eventmesh-runtime_1.0.0.tar.gz
tar -zxvf eventmesh-runtime_1.0.0.tar.gz
cd conf
config your proxy.properties
cd ../bin
sh start.sh
```
If you see "ProxyTCPServer[port=10000] started....", you setup runtime successfully.



## 2 Run Locally

### 2.1 dependencies

Same with 1.1

### 2.2 download sources

Same with 1.2

### 2.3 Run

**2.3.1 Project structure：**

![](../../images/project-structure.png)

- eventmesh-common : eventmesh common classes and method module
- eventmesh-connector-api : eventmesh connector api definition module
- eventmesh-connector-defibus : eventmesh defibus connector module
- eventmesh-connector-rocketmq : eventmesh rocketmq connector module
- eventmesh-runtime : eventmesh runtime module
- eventmesh-sdk-java : eventmesh java client sdk
- eventmesh-starter : eventmesh project local start entry

ps：The loading of connector plugin follows the Java SPI mechanism,  it's necessary to configure the mapping file of related interface and implementation class under /main/resources/meta-inf/services in the corresponding module

**2.3.2 Configure VM Options**

```java
-Dlog4j.configurationFile=..\eventmesh-runtime\conf\log4j2.xml
-Dproxy.log.home=..\eventmesh-runtime\logs
-Dproxy.home=..\eventmesh-runtime
-DconfPath=..\eventmesh-runtime\conf
```

**2.3.3 Configure build.gradle file**

Specify the connector that will be loaded after the project start with updating compile project item in dependencies

> default load eventmesh-connector-defibus connector 

```java
dependencies {
    compile project(":eventmesh-runtime"), project(":eventmesh-connector-defibus")
}
```

load rocketmq connector configuration：

```java
dependencies {
    compile project(":eventmesh-runtime"), project(":eventmesh-connector-rocketmq")
}
```

**2.3.4 Run**

running com.webank.eventmesh.starter.StartUp main method

