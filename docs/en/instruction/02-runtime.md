# EventMesh Runtime

EventMesh Runtime is the core component of Apache EventMesh (Incubating). It is the middleware that transmits events between producers and consumers. The documentation introduces the step to install and start the latest release of EventMesh Runtime in the local or test environment. The EventMesh Runtime requires a Linux-based system with JDK (Java Development Kit) 8+. 

Here, we take JDK 8 as an example. JDK 8 could be installed with the system package manager or the [openjdk:8-jdk](https://hub.docker.com/_/openjdk) Docker image.


## 1 Run on your local machine 

### 1.1 Dependencies

```
64-bit OS，we recommend Linux/Unix；
64-bit JDK 1.8+;
Gradle 7.0+, we recommend 7.0.*
4g+ available disk to deploy eventmesh-store
If you choose standalone mode, you could skip this file and go to the next step: Start Eventmesh-Runtime; if not, you could choose RocketMQ as the store layer.
```

### 1.2 Download Source Code

Gradle is the build automation tool used by Apache EventMesh (Incubating). Please refer to the [offical guide](https://docs.gradle.org/current/userguide/installation.html) to install the latest release of Gradle.

Download and extract the source code of the latest release from [EventMesh download](https://eventmesh.apache.org/download).

```console
wget https://dlcdn.apache.org/incubator/eventmesh/{version}-incubating/apache-eventmesh-{version}-incubating-source.tar.gz

tar -xvzf apache-eventmesh-1.5.0-incubating-source.tar.gz
```

Build the source code with Gradle.

```console
cd apache-eventmesh-1.5.0-incubating-source
gradle clean dist
```

Edit the `eventmesh.properties` to change the configuration (e.g. TCP port, client blacklist) of EventMesh Runtime.

```console
cd dist
vim conf/eventmesh.properties
```

Execute the `start.sh` script to start the EventMesh Runtime server.

```console
bash bin/start.sh
```

### 1.3 Build and Load Plugins

Apache EventMesh (Incubating) introduces the SPI (Service Provider Interface) mechanism, which enables EventMesh to discover and load the plugins at runtime. The plugins could be installed with these methods:

- Gradle Dependencies: Declare the plugins as the build dependencies in `eventmesh-starter/build.gradle`.

```gradle
dependencies {
   implementation project(":eventmesh-runtime")

   // Example: Load the RocketMQ plugin
   implementation project(":eventmesh-connector-plugin:eventmesh-connector-rocketmq")
}
```

- Plugin directory: EventMesh loads the plugins in the `dist/plugin` directory based on `eventmesh.properties`. The `installPlugin` task of Gradle builds and moves the plugins into the `dist/plugin` directory.

```console
gradle installPlugin
```


## 2 Remote deployment
### 2.1 Dependencies

```
64-bit OS，we recommend Linux/Unix；
64-bit JDK 1.8+;
Gradle 7.0+, we recommend 7.0.*
4g+ available disk to deploy eventmesh-store
If you choose standalone mode, you could skip this file and go to the next step: Start Eventmesh-Runtime; if not, you could choose RocketMQ as the store layer.
```

### 2.2 Download
Download and extract the executable binaries of the latest release from [EventMesh download](https://eventmesh.apache.org/download).

```console
wget https://dlcdn.apache.org/incubator/eventmesh/1.5.0-incubating/apache-eventmesh-1.5.0-incubating-bin.tar.gz

tar -xvzf apache-eventmesh-1.5.0-incubating-bin.tar.gz
```

### 2.3 Deploy
Edit the `eventmesh.properties` to change the configuration (e.g. TCP port, client blacklist) of EventMesh Runtime. The executable binaries contain all plugins in the bundle, thus there's no need to build them from source code.

```console
cd apache-eventmesh-1.5.0-incubating
vim conf/eventmesh.properties
```

Execute the `start.sh` script to start the EventMesh Runtime server.

```console
bash bin/start.sh
```

