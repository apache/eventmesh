# Quick start Instarction

### dependencies
```
64bit OS, Linux/Unix/Mac is recommended;
64bit JDK 1.8+;
Gradle 3.x;
4g+ free disk for Broker server
```

### download and build

```
download from git
unzip defibus-master.zip
cd defibus-master
gradle clean dist tar -x test

You can get a tar.gz package in directory named 'build'
```

### Deployment

deploy DeFiBusNamesrv
```
tar -zxvf DeFiBus_1.0.0.tar.gz
cd bin
sh runnamesrv.sh
```

deploy DeFiBusBroker
```
tar -zxvf DeFiBus_1.0.0.tar.gz
cd bin
sh runbroker.sh
```
