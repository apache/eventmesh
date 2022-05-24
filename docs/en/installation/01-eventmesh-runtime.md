# EventMesh Runtime

EventMesh Runtime is the core component of Apache EventMesh (Incubating). It is the middleware that transmits events between producers and consumers. The documentation introduces the step to install and start the latest release of EventMesh Runtime in the local or test environment. The EventMesh Runtime requires a Linux-based system with JDK (Java Development Kit) 8. JDK 8 could be installed with the system package manager or the [openjdk:8-jdk](https://hub.docker.com/_/openjdk) Docker image.

```console
# Debian, Ubuntu, etc.
apt-get install openjdk-8-jdk

# Fedora, Oracle Linux, Red Hat Enterprise Linux, etc.
yum install java-1.8.0-openjdk-devel

# Docker
docker pull openjdk:8-jdk
```

## Install Executable Binaries

Download and extract the executable binaries of the latest release (v1.4.0) from [GitHub releases](https://github.com/apache/incubator-eventmesh/releases).

```console
wget https://github.com/apache/incubator-eventmesh/releases/download/v1.4.0/apache-eventmesh-1.4.0-incubating-bin.tar.gz

tar -xvzf apache-eventmesh-1.4.0-incubating-bin.tar.gz
```

Edit the `eventmesh.properties` to change the configuration (e.g. TCP port, client blacklist) of EventMesh Runtime. The executable binaries contain all plugins in the bundle, thus there's no need to build them from source code.

```console
cd apache-eventmesh-1.4.0-incubating
vim conf/eventmesh.properties
```

Execute the `start.sh` script to start the EventMesh Runtime server.

```console
bash bin/start.sh
```

## Build from Source Code

Gradle is the build automation tool used by Apache EventMesh (Incubating). Please refer to the [offical guide](https://docs.gradle.org/current/userguide/installation.html) to install the latest release of Gradle.

Download and extract the source code of the latest release (v1.4.0) from [GitHub releases](https://github.com/apache/incubator-eventmesh/releases).

```console
wget https://github.com/apache/incubator-eventmesh/archive/refs/tags/v1.4.0.tar.gz

tar -xvzf v1.4.0.tar.gz
```

Build the source code with Gradle.

```console
cd incubator-eventmesh-1.4.0
gradle clean build
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

### Build and Load Plugins

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
