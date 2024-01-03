<div align="center">

<br /><br />
<img src="resources/logo.png" width="256">
<br />

[![CI status](https://img.shields.io/github/actions/workflow/status/apache/eventmesh/ci.yml?logo=github&style=for-the-badge)](https://github.com/apache/eventmesh/actions/workflows/ci.yml)
[![CodeCov](https://img.shields.io/codecov/c/gh/apache/eventmesh/master?logo=codecov&style=for-the-badge)](https://codecov.io/gh/apache/eventmesh)
[![Code Quality: Java](https://img.shields.io/lgtm/grade/java/g/apache/eventmesh.svg?logo=lgtm&logoWidth=18&style=for-the-badge)](https://lgtm.com/projects/g/apache/eventmesh/context:java)
[![Total Alerts](https://img.shields.io/lgtm/alerts/g/apache/eventmesh.svg?logo=lgtm&logoWidth=18&style=for-the-badge)](https://lgtm.com/projects/g/apache/eventmesh/alerts/)

[![License](https://img.shields.io/github/license/apache/eventmesh?style=for-the-badge)](https://www.apache.org/licenses/LICENSE-2.0.html)
[![GitHub Release](https://img.shields.io/github/v/release/apache/eventmesh?style=for-the-badge)](https://github.com/apache/eventmesh/releases)
[![Slack Status](https://img.shields.io/badge/slack-join_chat-blue.svg?logo=slack&style=for-the-badge)](https://join.slack.com/t/the-asf/shared_invite/zt-1y375qcox-UW1898e4kZE_pqrNsrBM2g)
  

[📦 Documentation](https://eventmesh.apache.org/docs/introduction) |
[📔 Examples](https://github.com/apache/eventmesh/tree/master/eventmesh-examples) |
[⚙️ Roadmap](https://eventmesh.apache.org/docs/roadmap) |
[🌐 简体中文](README.zh-CN.md)
</div>


# Apache EventMesh

**Apache EventMesh** is a new generation serverless event middleware for building distributed [event-driven](https://en.wikipedia.org/wiki/Event-driven_architecture) applications.

### EventMesh Architecture

![EventMesh Architecture](resources/eventmesh-architecture-4.png)

### EventMesh Dashboard
![EventMesh Dashboard](resources/dashboard.png)

## Features

Apache EventMesh has a vast amount of features to help users achieve their goals. Let us share with you some of the key features EventMesh has to offer:

- Built around the [CloudEvents](https://cloudevents.io) specification.
- Rapidty extendsible interconnector layer [connectors](https://github.com/apache/eventmesh/tree/master/eventmesh-connectors) using [openConnect](https://github.com/apache/eventmesh/tree/master/eventmesh-openconnect) such as the source or sink of Saas, CloudService, and Database etc.
- Rapidty extendsible storage layer such as [Apache RocketMQ](https://rocketmq.apache.org), [Apache Kafka](https://kafka.apache.org), [Apache Pulsar](https://pulsar.apache.org), [RabbitMQ](https://rabbitmq.com), [Redis](https://redis.io).
- Rapidty extendsible meta such as [Consul](https://consulproject.org/en/), [Nacos](https://nacos.io), [ETCD](https://etcd.io) and [Zookeeper](https://zookeeper.apache.org/).
- Guaranteed at-least-once delivery.
- Deliver events between multiple EventMesh deployments.
- Event schema management by catalog service.
- Powerful event orchestration by [Serverless workflow](https://serverlessworkflow.io/) engine.
- Powerful event filtering and transformation.
- Rapid, seamless scalability.
- Easy Function develop and framework integration.

## Roadmap
Please go to the [roadmap](https://eventmesh.apache.org/docs/roadmap) to get the release history and new features of Apache EventMesh.

## Subprojects
- [EventMesh-site](https://github.com/apache/eventmesh-site): Apache official website resources for EventMesh.
- [EventMesh-workflow](https://github.com/apache/eventmesh-workflow): Serverless workflow runtime for event Orchestration on EventMesh.
- [EventMesh-dashboard](https://github.com/apache/eventmesh-dashboard): Operation and maintenance console of EventMesh.
- [EventMesh-catalog](https://github.com/apache/eventmesh-catalog): Catalog service for event schema management using AsyncAPI.
- [EventMesh-go](https://github.com/apache/eventmesh-go): A go implementation for EventMesh runtime.

## Quick start

This section of the guide will walk you through the deployment steps for EventMesh: 

- [Deployment EventMesh Store](#deployment-eventmesh-store)
- [Deployment EventMesh Runtime](#deployment-eventmesh-runtime-)
    - [Run on your local machine](#run-on-your-local-machine)
        - [Run from source code](#1run-from-source-code)
        - [Run form local binary](#2-run-form-local-binary)
    - [Remote deployment](#remote-deployment)
    - [EventMesh Runtime with Docker](#eventmesh-runtime-with-docker)
- [eventmesh-sdk-java demo](#eventmesh-sdk-java-demo)
    - [TCP](#1tcp)
    - [HTTP](#2http)
    - [GRPC](#3grpc)
    - [Run Demo with shell scripts](#4run-demo-with-shell-scripts-)
- [Run EventMesh-Operator](#run-eventmesh-operator)
    - [Local source code run](#local-source-code-run)

### Deployment EventMesh Store

> EventMesh now supports `standalone`, `RocketMQ`, `Kafka` and other middleware as a storage.      
> If you are in non-`standalone` mode, you need to deploy the required `store` first, using `rocketmq` mode as an example: Deploy [RocketMQ](https://rocketmq.apache.org/docs/quickStart/01quickstart/).  

### Deployment EventMesh Runtime  

The EventMesh Runtime is a stateful mesh node in an EventMesh cluster that is responsible for event transfer between the Source Connector and the Sink Connector, and can use Event Store as a storage queue for events.  

#### Run on your local machine

Dependencies:
- 64-bit OS, we recommend Linux/Unix.
- 64-bit JDK 1.8 or JDK 11
- Gradle 7.0+, The recommended version can be found in the `gradle/wrapper/gradle-wrapper.properties` file.

##### 1）Run from source code

1.Download source code:   
Download and extract the source code of the latest release from [EventMesh download](https://eventmesh.apache.org/download/).For example, with the current latest version, you will get `apache-eventmesh-1.10.0-source.tar.gz`.

There are two ways to install the plugin:

- classpath loading: Local developers can install the plugin by declaring it in the eventmesh-starter module build.gradle, e.g., declare that it uses the rocketmq plugin  
```
implementation project(":eventmesh-connectors:eventmesh-connector-rocketmq")
```

- File loading: By installing the plugin to the plugin directory, EventMesh will automatically load the plugins in the plugin directory according to the conditions at runtime, you can install the plugin by executing the following command  
```
./gradlew clean jar dist && ./gradlew installPlugin
```

3.Using Plugins   

EventMesh will load plugins in the `dist/plugin` directory by default, you can change the plugin directory with `-DeventMeshPluginDir=your_plugin_directory`. Examples of plugins to be used at runtime can be found in the `confPath` directory under `eventmesh.properties`. For example declare the use of the rocketmq plugin at runtime with the following settings.  
```
#connector plugin
eventMesh.connector.plugin.type=rocketmq
```

4.Configuring the VM startup parameters  
```
-Dlog4j.configurationFile=eventmesh-runtime/conf/log4j2.xml
-Deventmesh.log.home=eventmesh-runtime/logs
-Deventmesh.home=eventmesh-runtime
-DconfPath=eventmesh-runtime/conf
```
> Note: If your operating system is Windows, you may need to replace the file separator with `'\'`.

5.Getting up and running
```
Run org.apache.eventmesh.starter.
```

##### 2) Run form local binary

1.Download Source Code

Download and extract the source code of the latest release from [EventMesh download](https://eventmesh.apache.org/download/).For example, with the current latest version, you will get apache-eventmesh-1.10.0-source.tar.gz.
```
tar -xvzf apache-eventmesh-1.10.0-source.tar.gz
cd apache-eventmesh-1.10.0-src/
```

Build the source code with Gradle.
```
gradle clean dist
```

Edit the `eventmesh.properties` to change the configuration (e.g. TCP port, client blacklist) of EventMesh Runtime.  
```
cd dist
vim conf/eventmesh.properties
```

2.Build and Load Plugins    
Apache EventMesh introduces the SPI (Service Provider Interface) mechanism, which enables EventMesh to discover and load the plugins at runtime.The plugins could be installed with these methods:  
- Gradle Dependencies: Declare the plugins as the build dependencies in `eventmesh-starter/build.gradle`.  
```
dependencies {
   implementation project(":eventmesh-runtime")

    // Example: Load the RocketMQ plugin
   implementation project(":eventmesh-connectors:eventmesh-connector-rocketmq")
}
```

- Plugin directory: EventMesh loads the plugins in the `dist/plugin` directory based on `eventmesh.properties`. The `installPlugin` task of Gradle builds and moves the plugins into the `dist/plugin` directory.
```
gradle installPlugin
```

3.Start Runtime

Execute the `start.sh` script to start the EventMesh Runtime server.
```
bash bin/start.sh
```

View the output log:  
```
tail -f logs/eventmesh.out
```

#### Remote deployment

1.Download:

Download and extract the executable binaries of the latest release from[EventMesh download](https://eventmesh.apache.org/download/).For example, with the current latest version, you will get `apache-eventmesh-1.10.0.tar.gz`.  
```
tar -xvzf apache-eventmesh-1.10.0-bin.tar.gz
cd apache-eventmesh-1.10.0
```

2.Deploy  

Edit the `eventmesh.properties` to change the configuration (e.g. TCP port, client blacklist) of EventMesh Runtime. The executable binaries contain all plugins in the bundle, thus there's no need to build them from source code.
```
vim conf/eventmesh.properties
```

Execute the `start.sh` script to start the EventMesh Runtime server.
```
bash bin/start.sh
```
If you see `EventMeshTCPServer[port=10000] started....`, then the setup was successful.

View the output log:
```
cd /root/apache-eventmesh-1.10.0/logs
tail -f eventmesh.out
```

You can stop the run with the following command:  
```
bash bin/stop.sh
```

#### EventMesh Runtime with Docker

Dependencies:  
- 64-bit OS, we recommend Linux/Unix.   
- 64-bit JDK 1.8 or JDK 11.    
- Gradle 7.0+, The recommended version can be found in the `gradle/wrapper/gradle-wrapper.properties` file.  

1.Pull EventMesh Image  
Download the pre-built image of [eventmesh](https://hub.docker.com/r/apache/eventmesh) from Docker Hub with docker pull:
```
sudo docker pull apache/eventmesh:v1.10.0
```

To verify that the apache/eventmesh image is successfully installed, list the downloaded images with docker images:  
```
$ sudo docker images
REPOSITORY            TAG       IMAGE ID       CREATED         SIZE
apache/eventmesh   v1.10.0    6e2964599c78     10 days ago     937MB
```

2.Edit Configuration:    
Edit the `eventmesh.properties` to change the configuration (e.g. TCP port, client blacklist) of EventMesh Runtime. To integrate RocketMQ as a connector, these two configuration files should be created: `eventmesh.properties` and `rocketmq-client.properties`.
```
sudo mkdir -p /data/eventmesh/rocketmq/conf
cd /data/eventmesh/rocketmq/conf
sudo touch eventmesh.properties
sudo touch rocketmq-client.properties
```

3.Configure `eventmesh.properties`   

The `eventmesh.properties` file contains the properties of EventMesh Runtime environment and integrated plugins.Please refer to the [default configuration file](https://github.com/apache/eventmesh/blob/1.10.0-prepare/eventmesh-runtime/conf/eventmesh.properties) for the available configuration keys.  
```
sudo vim eventmesh.properties
``` 

Please check if the default port in the configuration file is occupied, if it is occupied please change it to an unoccupied port:

| Configuration Key            | Default Value | Description                  |   
|------------------------------|---------------|------------------------------|  
| `eventMesh.server.http.port` | `10105`       | `EventMesh http server port` |  
| `eventMesh.server.tcp.port`  | `10000`       | `EventMesh tcp server port`  | 
| `eventMesh.server.grpc.port` | `10205`       | `EventMesh grpc server port` | 

4.Configure `rocketmq-client.properties`

The `rocketmq-client.properties` file contains the properties of the Apache RocketMQ nameserver.

```
sudo vim eventmesh.properties
```
Please refer to the [default configuration file](https://github.com/apache/eventmesh/blob/1.10.0-prepare/eventmesh-storage-plugin/eventmesh-storage-rocketmq/src/main/resources/rocketmq-client.properties) and change the value of eventMesh.server.rocketmq.namesrvAddr to the nameserver address of RocketMQ.
> Note that if the `nameserver` address you are running is not the default in the configuration file, change it to the `nameserver` address that is actually running.

Please check if the `default namesrvAddr` in the configuration file is occupied, if it is occupied please change it to an unoccupied address:

| Configuration Key	                      | Default Value	                  | Description                        |   
|-----------------------------------------|---------------------------------|------------------------------------|  
| `eventMesh.server.rocketmq.namesrvAddr` | `127.0.0.1:9876;127.0.0.1:9876` | `RocketMQ namesrv default address` |

5.Run and Manage EventMesh Container  

Run an EventMesh container from the `apache/eventmesh` image with the `docker run` command.  
- The `-p` option of the command binds the container port with the host machine port.
- The `-v` option of the command mounts the configuration files from files in the host machine.  
- 
```
sudo docker run -d \
    -p 10000:10000 -p 10105:10105 \
    -v /data/eventmesh/rocketmq/conf/eventMesh.properties:/data/app/eventmesh/conf/eventMesh.properties \
    -v /data/eventmesh/rocketmq/conf/rocketmq-client.properties:/data/app/eventmesh/conf/rocketmq-client.properties \
    apache/eventmesh:v1.10.0
```
If you see a new line of output after running the command, the container running the EventMesh image has started successfully.

The docker ps command lists the details (id, name, status, etc.) of the running containers. The container id is the unique identifier of the container.
```
$ sudo docker ps
CONTAINER ID   IMAGE                        COMMAND                  CREATED         STATUS         PORTS                                                                                          NAMES
5bb6b6092672   apache/eventmesh:v1.10.0   "/bin/sh -c 'sh star…"     5 seconds ago   Up 3 seconds   0.0.0.0:10000->10000/tcp, :::10000->10000/tcp, 0.0.0.0:10105->10105/tcp, :::10105->10105/tcp   eager_driscoll
```

6.Managing EventMesh Containers     
After successfully running an EventMesh container, you can manage the container by entering it, viewing logs, deleting it, and so on.  
To connect to the EventMesh container:
```
sudo docker exec -it [your container id or name] /bin/bash
```

To read the log of the EventMesh container:
```
cd ../logs
tail -f eventmesh.out
```

To stop or remove the container:
```
sudo docker stop [container id or name]
sudo docker rm -f [container id or name]
```

### eventmesh-sdk-java demo

> eventmesh-sdk-java acts as a client of EventMesh Runtime and communicates with it, to publish and subscribe the messages.   
>  
> The eventmesh-sdk-java supports ASYNC messages and BROADCAST messages. ASYNC messages indicate that producers only send messages and do not care about receiving reply messages. BROADCAST messages mean that producers send a message once, and all consumers subscribed to the broadcast topic will receive the message.     
>  
> eventmesh-sdk-java supports HTTP, TCP and gRPC protocols.  

The test demos of TCP, HTTP and GRPC are in the module `eventmesh-examples`.  

#### 1.TCP

##### 1.1 ASYNC

- Start consumer to subscribe the topic (we have created the `TEST-TOPIC-TCP-ASYNC` by default, you can also create other topic to test)
```
Run the main method of org.apache.eventmesh.tcp.demo.sub.eventmeshmessage.AsyncSubscribe
```

- Start producer to publish async message
```
Run the main method of org.apache.eventmesh.tcp.demo.pub.eventmeshmessage.AsyncPublish
```

##### 1.2 BROADCAST
- Start subscriber to subscribe the topic (we have created the `TEST-TOPIC-TCP-BROADCAST` by default, you can also create other topic to test)
```
Run the main method of org.apache.eventmesh.tcp.demo.sub.eventmeshmessage.AsyncSubscribeBroadcast
```
- Start publisher to publish async message
````
Run the main method of org.apache.eventmesh.tcp.demo.pub.eventmeshmessage.AsyncPublishBroadcast
````

#### 2.HTTP
> For HTTP, the eventmesh-sdk-java implements sending and subscribing to asynchronous events.  
> 
> In the demo, the `content` field of the Java class `EventMeshMessage` represents a special protocol. Therefore, if you are using the eventmesh-sdk-java's http-client, you only need to design the content of the protocol and provide the consumer's application at the same time.   

##### 2.1 ASYNC
- The subscriber is a SpringBoot demo, so run this demo to start subscriber (we have created the topic `TEST-TOPIC-HTTP-ASYNC` by default, you can also create other topic to test)
```
Run the main method of org.apache.eventmesh.http.demo.sub.SpringBootDemoApplication
```

> Start publisher to publish message
```
Run the main method of org.apache.eventmesh.http.demo.pub.eventmeshmessage.AsyncPublishInstance
```

#### 3.GRPC

> The eventmesh-sdk-java implements the gRPC protocol. It can asynchronously or synchronously send events to the EventMesh Runtime.  
> 
> It can subscribe to consume events through Webhook and event streaming, and also supports the CNCF CloudEvents protocol. 

##### 3.1 ASYNC Publish & Webhook Subscribe
> Producers can asynchronously send events to the EventMesh Runtime without waiting for the events to be stored in the Event Store.
> 
> For Webhook consumers, events will be pushed to the consumer's HTTP Endpoint URL, i.e., the consumer's `subscribeUrl`. This method is similar to the previously mentioned Http EventMesh client.  

- Start publisher to publish message (we have created the topic `TEST-TOPIC-GRPC-ASYNC` by default, you can also create other topic to test)
```
Run the main method of org.apache.eventmesh.grpc.pub.eventmeshmessage.AsyncPublishInstance
```

- Start webhook subscriber
```
Run the main method of org.apache.eventmesh.grpc.sub.app.SpringBootDemoApplication
```

##### 3.2 SYNC Publish & Stream Subscribe
> Producers synchronously send events to the EventMesh Runtime while waiting for the events to be stored in the Event Store.  
> 
> For event stream consumers, events are pushed in a streaming to the ReceiveMsgHook client. This method is similar to the EventMesh client.  

- Start Request-Reply publisher to publish message (we have created the topic `TEST-TOPIC-GRPC-RR` by default, you can also create other topic to test)
```
Run the main method of org.apache.eventmesh.grpc.pub.eventmeshmessage.RequestReplyInstance
```

- Start stream subscriber
```
Run the main method of org.apache.eventmesh.grpc.sub.EventmeshAsyncSubscribe
```

##### 3.3 Publish BATCH Message
> Asynchronously batch publish multiple events to the EventMesh Runtime.
- Start publisher to publish batch message (we have created the `TEST-TOPIC-GRPC-ASYNC` by default, you can also create other topic to test.)
```
Run the main method of org.apache.eventmesh.grpc.pub.eventmeshmessage.BatchPublishInstance
```

#### 4.Run Demo with shell scripts  

Please refer to [Event Store](#deployment-eventmesh-store) and [EventMesh Runtime](#deployment-eventmesh-runtime-) to finish the necessary deployment before try our demo.

After finishing the deployment of Store and Runtime, you can run our demos in module `eventmesh-examples`:

gradle：
```
cd apache-eventmesh-1.10.0-src/eventmesh-examples
gradle clean dist
cd ./dist/bin
```

##### 4.1 TCP

TCP Sub
```
bash tcp_eventmeshmessage_sub.sh
```

Open the corresponding log file to view the log:
```
cd /root/apache-eventmesh-1.10.0-src/eventmesh-examples/dist/logs
tail -f demo_tcp_pub.out
```

TCP Pub
```
bash tcp_pub_eventmeshmessage.sh
```

Open the corresponding log file to view the log:  
```
cd /root/apache-eventmesh-1.10.0-src/eventmesh-examples/dist/logs
tail -f demo_tcp_sub.out
```

##### 4.2 TCP Broadcast

TCP Sub Broadcast
```
sh tcp_sub_eventmeshmessage_broadcast.sh
```

Open the corresponding log file to view the log:
```
cd /root/apache-eventmesh-1.10.0-src/eventmesh-examples/dist/logs
tail -f demo_tcp_sub_broadcast.out
```

TCP Pub Broadcast
```
sh tcp_pub_eventmeshmessage_broadcast.sh
```
 
Open the corresponding log file to view the log:
```
cd /root/apache-eventmesh-1.10.0-src/eventmesh-examples/dist/logs
tail -f demo_tcp_pub_broadcast.out
```

##### 4.3 HTTP

HTTP Sub
```
sh http_sub.sh
```

Open the corresponding log file to view the log:
```
cd /root/apache-eventmesh-1.10.0-src/eventmesh-examples/dist/logs
tail -f demo_http_sub.out
```

HTTP Pub
```
sh http_pub_eventmeshmessage.sh
```

Open the corresponding log file to view the log:
```
cd /root/apache-eventmesh-1.10.0-src/eventmesh-examples/dist/logs
tail -f demo_http_pub.out
```
You can see the run logs for the different modes under the `/logs` directory.

### Run EventMesh-Operator

Dependencies:  
- docker
- golang (version 1.19)
- kubernetes (kubectl)  
> There is some compatibility between kubernetes an docker, please check the version compatibility between them and download the corresponding version to ensure that they work properly together.

#### Local source code run

1.Start:  

Go to the eventmesh-operator directory.  
```
cd eventmesh-operator
```

Install CRD into the specified k8s cluster.
```
make install

# Uninstall CRDs from the K8s cluster
make uninstall
```

If you get error `eventmesh-operator/bin/controller-gen: No such file or directory`    
Run the following command:  
```
# download controller-gen locally if necessary.
make controller-gen
# download kustomize locally if necessary.
make kustomize
```

View crds information:  
```
# run the following command to view crds information:
kubectl get crds
NAME                                      CREATED AT
connectors.eventmesh-operator.eventmesh   2023-11-28T01:35:21Z
runtimes.eventmesh-operator.eventmesh     2023-11-28T01:35:21Z
```

run eventmesh-operator:
```
# run controller
make run
```

2.Create and delete CRs:  
Custom resource objects are located at: `/config/samples` 
When deleting CR, simply replace `create` with `delete`.   
```
# Create CR for eventmesh-runtime、eventmesh-connector-rocketmq,Creating a clusterIP lets eventmesh-runtime communicate with other components.
make create

#success:
configmap/runtime-config created
runtime.eventmesh-operator.eventmesh/eventmesh-runtime created
service/runtime-cluster-service created
configmap/connector-rocketmq-config created
connectors.eventmesh-operator.eventmesh/connector-rocketmq created

# View the created Service.
kubectl get service
NAME                      TYPE        CLUSTER-IP      EXTERNAL-IP   PORT(S)     AGE
runtime-cluster-service   ClusterIP   10.109.209.72   <none>        10000/TCP   17s

# After the pods are successfully started, run the following command to view pods.
kubectl get pods
NAME                      READY   STATUS    RESTARTS   AGE
connector-rocketmq-0      1/1     Running   0          12m
eventmesh-runtime-0-a-0   1/1     Running   0          12m

# delete CR
make delete
```

## Contributing

Each contributor has played an important role in promoting the robust development of Apache EventMesh. We sincerely appreciate all contributors who have contributed code and documents.

- [Contributing Guideline](https://eventmesh.apache.org/community/contribute/contribute)
- [Good First Issues](https://github.com/apache/eventmesh/issues?q=is%3Aopen+is%3Aissue+label%3A%22good+first+issue%22)

Here is the [List of Contributors](https://github.com/apache/eventmesh/graphs/contributors), thank you all! :)

<a href="https://github.com/apache/eventmesh/graphs/contributors">
  <img src="https://contrib.rocks/image?repo=apache/eventmesh" />
</a>


## CNCF Landscape

<div align="center">

<img src="https://landscape.cncf.io/images/left-logo.svg" width="150"/>
<img src="https://landscape.cncf.io/images/right-logo.svg" width="200"/>

Apache EventMesh enriches the <a href="https://landscape.cncf.io/serverless?license=apache-license-2-0">CNCF Cloud Native Landscape.</a>

</div>

## License

Apache EventMesh is licensed under the [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.html).

## Community

|WeChat Assistant|WeChat Public Account|Slack|
|-|-|-|
|<img src="resources/wechat-assistant.jpg" width="128"/>|<img src="resources/wechat-official.jpg" width="128"/>|[Join Slack Chat](https://join.slack.com/t/the-asf/shared_invite/zt-1y375qcox-UW1898e4kZE_pqrNsrBM2g)(Please open an issue if this link is expired)|

Bi-weekly meeting : [#Tencent meeting](https://meeting.tencent.com/dm/wes6Erb9ioVV) : 346-6926-0133

Bi-weekly meeting record : [bilibili](https://space.bilibili.com/1057662180)

### Mailing List

|Name|Description|Subscribe|Unsubscribe|Archive
|-|-|-|-|-|
|Users|User discussion|[Subscribe](mailto:users-subscribe@eventmesh.apache.org)|[Unsubscribe](mailto:users-unsubscribe@eventmesh.apache.org)|[Mail Archives](https://lists.apache.org/list.html?users@eventmesh.apache.org)|
|Development|Development discussion (Design Documents, Issues, etc.)|[Subscribe](mailto:dev-subscribe@eventmesh.apache.org)|[Unsubscribe](mailto:dev-unsubscribe@eventmesh.apache.org)|[Mail Archives](https://lists.apache.org/list.html?dev@eventmesh.apache.org)|
|Commits|Commits to related repositories| [Subscribe](mailto:commits-subscribe@eventmesh.apache.org) |[Unsubscribe](mailto:commits-unsubscribe@eventmesh.apache.org) |[Mail Archives](https://lists.apache.org/list.html?commits@eventmesh.apache.org)|
|Issues|Issues or PRs comments and reviews| [Subscribe](mailto:issues-subscribe@eventmesh.apache.org) |[Unsubscribe](mailto:issues-unsubscribe@eventmesh.apache.org) |[Mail Archives](https://lists.apache.org/list.html?issues@eventmesh.apache.org)|
