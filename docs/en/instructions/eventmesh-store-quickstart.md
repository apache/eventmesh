# Eventmesh-store Quick start Instruction

### dependencies

```
64bit OS, Linux/Unix is recommended;
64bit JDK 1.8+;
Gradle at least 5.6, eg 5.6.*
4g+ free disk for eventmesh-store server
```

### download sources

download source code from [https://github.com/WeBankFinTech/DeFiBus](https://github.com/WeBankFinTech/DeFiBus)  
You will get **DefiBus-master.zip**

### build sources

The eventmesh-store takes DeFiBus for example at the following parts, because eventmesh depends on defibus as store layer
by default, other implements such as Rocketmq etc. is coming soon.

```
unzip DefiBus-master.zip
cd /*YOUR DEPLOY PATH*/DefiBus-master
gradle clean dist tar -x test
```

You will get **DeFiBus_1.0.0.tar.gz** in directory /* YOUR DEPLOY PATH */DefiBus-master/build

### Deployment

- deploy DeFiBusNamesrv

```
upload DeFiBus_1.0.0.tar.gz
tar -zxvf DeFiBus_1.0.0.tar.gz
cd bin
sh runnamesrv.sh
```

If you see "Thre Name Server boot success" in ../logs/namesrv.log, you setup DeFiBus Namesrv successfully.

- deploy DeFiBusBroker

```
upload DeFiBus_1.0.0.tar.gz
tar -zxvf DeFiBus_1.0.0.tar.gz
cd conf
config your broker.properties
cd ../bin
sh runbroker.sh
```

If you see "The broker \[YOUR-BROKER-NAME, IP:PORT\] boot success." in ../logs/broker.log, you setup eventmesh-store
successfully.

