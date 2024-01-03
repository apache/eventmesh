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
[![Slack Status](https://img.shields.io/badge/slack-join_chat-blue.svg?logo=slack&style=for-the-badge)](https://join.slack.com/t/apacheeventmesh/shared_invite/zt-1t1816dli-I0t3OE~IpdYWrZbIWhMbXg)

[📦 文档(英文)](https://eventmesh.apache.org/docs/introduction) |
[📔 例子](https://github.com/apache/eventmesh/tree/master/eventmesh-examples) |
[⚙️ 路线图](https://eventmesh.apache.org/docs/roadmap) |
[🌐 英文版](README.zh-CN.md)
</div>


# Apache EventMesh

**Apache EventMesh** 是用于构建分布式[事件驱动](https://en.wikipedia.org/wiki/Event-driven_architecture)应用程序的新一代无服务器事件中间件。

### EventMesh 架构

![EventMesh Architecture](resources/eventmesh-architecture-4.png)

### EventMesh Dashboard

![EventMesh Dashboard](resources/dashboard.png)

## 特性

Apache EventMesh提供了许多功能来帮助用户实现他们的目标，以下是一些EventMesh的关键特点：

- 基于 [CloudEvents](https://cloudevents.io) 规范构建。
- 快速可扩展的Connector，[connectors](https://github.com/apache/eventmesh/tree/master/eventmesh-connectors)，例如作为Saas、CloudService和数据库等的source 或sink。.
- 快速可扩展的存储层，使用 [JDBC](https://en.wikipedia.org/wiki/Java_Database_Connectivity)和[Apache RocketMQ](https://rocketmq.apache.org), [Apache Kafka](https://kafka.apache.org), [Apache Pulsar](https://pulsar.apache.org), [RabbitMQ](https://rabbitmq.com), [Redis](https://redis.io), [Pravega](https://cncf.pravega.io), 和 [RDMS](https://en.wikipedia.org/wiki/Relational_database)（正在进行中）集成。
- 快速可扩展的控制器，例如 [Consul](https://consulproject.org/en/), [Nacos](https://nacos.io), [ETCD](https://etcd.io) 和 [Zookeeper](https://zookeeper.apache.org/)。
- 至少一次的可靠性投递。
- 在多个EventMesh部署之间传递事件。
- 通过目录服务进行事件模式管理。
- 通过 [Serverless workflow](https://serverlessworkflow.io/) 引擎实现强大的事件编排。
- 强大的事件过滤和转换功能。
- 快速、无缝的可扩展性。
- 易于函数开发和框架集成。

## 路线图

请前往[路线图](https://eventmesh.apache.org/docs/roadmap)查看Apache EventMesh的版本历史和新功能。.

## 子项目

- [EventMesh-site](https://github.com/apache/eventmesh-site): Apache EventMesh 的官方网站资源。
- [EventMesh-workflow](https://github.com/apache/eventmesh-workflow): 用于在 EventMesh 上进行事件编排的无服务器工作流运行时。
- [EventMesh-dashboard](https://github.com/apache/eventmesh-dashboard): EventMesh 的运维控制台。
- [EventMesh-catalog](https://github.com/apache/eventmesh-catalog): 使用 AsyncAPI 进行事件模式管理的目录服务。
- [EventMesh-go](https://github.com/apache/eventmesh-go): EventMesh 运行时的 Go 语言实现。

## 快速入门  
本段指南将指导您完成EventMesh的部署步骤   
- [部署EventMesh Store](#部署eventmesh-store-)
- [部署EventMesh Runtime](#部署eventmesh-runtime)
  - [本地构建运行](#本地构建运行)
    - [源码启动](#1源码启动-)
    - [本地二进制构建](#2-本地二进制构建)
  - [远程部署](#远程部署)  
  - [Docker部署EventMesh Runtime](#docker部署eventmesh-runtime)
- [eventmesh-sdk-java demo](#eventmesh-sdk-java-demo-)
  - [TCP](#1tcp-)
  - [HTTP](#2http)
  - [GRPC](#3grpc)
  - [测试](#4测试-)
- [运行EventMesh-Operator](#运行eventmesh-operator)
  - [本地源码运行](#本地源码运行)  

### 部署EventMesh Store  

> EventMesh现在支持`standalone`、`RocketMQ`、`Kafka`等中间件作为存储   
> 如果是在非`standalone`模式下，需要先部署所需的`store`，以`rocketmq`模式为例: 部署[RocketMQ](https://rocketmq.apache.org/docs/quickStart/01quickstart/)

### 部署EventMesh Runtime
EventMesh Runtime是EventMesh集群中有状态的Mesh节点，负责Source Connector与Sink Connector之间的事件传输，并可以使用EventMesh Storage作为事件的存储队列。

#### 本地构建运行

依赖准备: 
```
建议使用64位操作系统，建议使用Linux / Unix；
64位JDK 1.8+;
Gradle至少为7.0, 推荐 7.0.*
```
##### 1）源码启动  

1.下载源码:   
从[EventMesh download](https://eventmesh.apache.org/download/)下载并提取最新版本的源代码。您将获得`apache-eventmesh-1.10.0-source.tar.gz`。

2.安装插件:   

有两种方式安装插件:  

- classpath加载: 本地开发可以通过在 eventmesh-starter 模块 build.gradle 中进行声明，例如声明使用 rocketmq 插件  
```
implementation project(":eventmesh-connectors:eventmesh-connector-rocketmq")
```

- 文件加载: 通过将插件安装到插件目录，EventMesh在运行时会根据条件自动加载插件目录下的插件，可以通过执行以下命令安装插件  
```
./gradlew clean jar dist && ./gradlew installPlugin
```

3.使用插件  
EventMesh 会默认加载`dist/plugin`目录下的插件，可以通过`-DeventMeshPluginDir=your_plugin_directory`来改变插件目录。运行时需要使用的插件实例可以在`confPath`目录下面的`eventmesh.properties`中进行配置。例如通过以下设置声明在运行时使用rocketmq插件。  
```
#connector plugin
eventMesh.connector.plugin.type=rocketmq
```

4.配置VM启动参数  
```
-Dlog4j.configurationFile=eventmesh-runtime/conf/log4j2.xml
-Deventmesh.log.home=eventmesh-runtime/logs
-Deventmesh.home=eventmesh-runtime
-DconfPath=eventmesh-runtime/conf
```
> 注：如果操作系统为Windows, 可能需要将文件分隔符换成`'\'`

5.启动运行
```
运行org.apache.eventmesh.starter.StartUp的主要方法
```

##### 2) 本地二进制构建

1.下载源码  

从[EventMesh download](https://eventmesh.apache.org/download/)下载并提取最新版本的源代码。比如目前最新版，您将获得`apache-eventmesh-1.9.0-source.tar.gz`。
```
tar -xvzf apache-eventmesh-1.10.0-source.tar.gz
cd apache-eventmesh-1.10.0-src/
```

使用 Gradle 构建源代码。
```
gradle clean dist
```

编辑`eventmesh.properties`以更改EventMesh Runtime的配置（如 TCP 端口、客户端黑名单）。
```
cd dist
vim conf/eventmesh.properties
```

2.构建并加载插件  
Apache EventMesh引入了 SPI 机制，使 EventMesh 能够在运行时发现并加载插件。有两种方式安装插件:  
- Gradle依赖项: 在`eventmesh-starter/build.gradle`中将插件声明为构建依赖项。  
```
dependencies {
   implementation project(":eventmesh-runtime")

    // 示例： 加载 RocketMQ 插件
   implementation project(":eventmesh-connectors:eventmesh-connector-rocketmq")
}
```

- 插件目录: EventMesh会根据`eventmesh.properties`加载`dist/plugin`目录中的插件。Gradle的`installPlugin`任务会构建插件并将其移动到`dist/plugin`目录中。  
```
gradle installPlugin
```

3.启动Runtime   

执行`start.sh`脚本启动EventMesh Runtime服务器。
```
bash bin/start.sh
```

查看输出日志：  
```
tail -f logs/eventmesh.out
```

#### 远程部署

在[EventMesh download](https://eventmesh.apache.org/download/)页面选择所需要版本的Binary Distribution进行下载,您将获得`apache-eventmesh-1.10.0-bin.tar.gz`。   
1.下载:  
```
# 解压
tar -xvzf apache-eventmesh-1.10.0-bin.tar.gz
cd apache-eventmesh-1.10.0
```

2.部署  

编辑`eventmesh.properties`以更改EventMesh Runtime的配置（如 TCP 端口、客户端黑名单）。
```
vim conf/eventmesh.properties
```

执行`start.sh`脚本启动EventMesh Runtime服务器。  
```
bash bin/start.sh
```
如果看到`EventMeshTCPServer[port=10000] started....`, 则说明设置成功。   

查看输出日志:  
```
cd /root/apache-eventmesh-1.10.0/logs
tail -f eventmesh.out
```

停止:  
```
bash bin/stop.sh
```

#### Docker部署EventMesh Runtime

准备:   
- 建议使用64位的linux系统。
- 请预先安装Docker Engine。Docker的安装过程可以参考[docker官方文档](https://docs.docker.com/engine/install/)。
- 建议掌握基础的docker概念和命令行，例如注册中心、挂载等等。不过这不是必须的，因为本次操作所需的命令都已为您列出。
- 若您选择非standalone模式，请确保[RocketMQ](https://rocketmq.apache.org/docs/quickStart/01quickstart/)已成功启动并且可以使用ip地址访问到；若您选择standalone模式，则无需启动RocketMQ 。

1.获取EventMesh镜像  
首先，你可以打开一个命令行，并且使用下面的`pull`命令从[Docker Hub](https://hub.docker.com)中下载最新发布的[EventMesh](https://hub.docker.com/r/apache/eventmesh)。   
```
sudo docker pull apache/eventmesh:v1.10.0
```

您可以使用以下命令列出并查看本地已有的镜像。
```
sudo docker images
```

如果终端显示如下所示的镜像信息，则说明 EventMesh 镜像已经成功下载到本地。
```
$ sudo docker images
REPOSITORY            TAG       IMAGE ID       CREATED         SIZE
apache/eventmesh     v1.10.0    6e2964599c78   16 months ago   937MB
```

2.创建配置文件:  
在根据EventMesh镜像运行对应容器之前，你需要创建两个配置文件，分别是:`eventmesh.properties`和`rocketmq-client.properties`。  
首先，你需要使用下面的命令创建这两个文件。  
```
sudo mkdir -p /data/eventmesh/rocketmq/conf
cd /data/eventmesh/rocketmq/conf
sudo touch eventmesh.properties
sudo touch rocketmq-client.properties
```

3.配置`eventmesh.properties`  

这个配置文件中包含 EventMesh 运行时环境和集成进来的其他插件所需的参数。  
使用下面的`vim`命令编辑`eventmesh.properties`。  
```
sudo vim eventmesh.properties
```
你可以直接将 GitHub 仓库中的对应[配置文件](https://github.com/apache/eventmesh/blob/1.10.0-prepare/eventmesh-runtime/conf/eventmesh.properties)中的内容复制过来。  

请检查配置文件里的默认端口是否已被占用，如果被占用请修改成未被占用的端口:    

| 属性                           | 默认值     | 备注                           |   
|------------------------------|---------|------------------------------|  
| `eventMesh.server.http.port` | `10105` | `EventMesh http server port` |  
| `eventMesh.server.tcp.port`  | `10000` | `EventMesh tcp server port`  | 
| `eventMesh.server.grpc.port` | `10205` | `EventMesh grpc server port` | 

4.配置`rocketmq-client.properties`  

这个配置文件中包含 EventMesh 运行时环境和集成进来的其他插件所需的参数。

使用下面的`vim`命令编辑`eventmesh.properties`。
```
sudo vim eventmesh.properties
```
你可以直接将GitHub仓库中的对应[配置文件](https://github.com/apache/eventmesh/blob/1.10.0-prepare/eventmesh-storage-plugin/eventmesh-storage-rocketmq/src/main/resources/rocketmq-client.properties)中的内容复制过来   
> 请注意，如果您正在运行的 namesetver 地址不是配置文件中的默认值，请将其修改为实际正在运行的nameserver地址。

请检查配置文件里的默认namesrvAddr是否已被占用，如果被占用请修改成未被占用的地址:

| 属性                                      | 默认值                             | 备注                                 |   
|-----------------------------------------|---------------------------------|------------------------------------|  
| `eventMesh.server.rocketmq.namesrvAddr` | `127.0.0.1:9876;127.0.0.1:9876` | `RocketMQ namesrv default address` |

5.运行EventMesh  
现在你就可以开始根据下载好的EventMesh镜像运行容器了。   
使用到的命令是`docker run`，有以下两点内容需要格外注意。  
- 绑定容器端口和宿主机端口: 使用`docker run`的`-p`选项。
- 将宿主机中的两份配置文件挂在到容器中: 使用`docker run`的`-v`选项。  

综合一下，对应的启动命令为:  
```
sudo docker run -d \
    -p 10000:10000 -p 10105:10105 \
    -v /data/eventmesh/rocketmq/conf/eventMesh.properties:/data/app/eventmesh/conf/eventMesh.properties \
    -v /data/eventmesh/rocketmq/conf/rocketmq-client.properties:/data/app/eventmesh/conf/rocketmq-client.properties \
    apache/eventmesh:v1.10.0
```
如果运行命令之后看到新输出一行字符串，那么运行 EventMesh 镜像的容器就启动成功了。  

接下来，你可以使用下面的命令查看容器的状态。  
```
sudo docker ps
```

如果成功的话，你会看到终端打印出了如下所示容器的信息，其中就有运行 EventMesh 镜像的容器。  
```
CONTAINER ID   IMAGE                        COMMAND                  CREATED         STATUS         PORTS                                                                                          NAMES
5bb6b6092672   apache/eventmesh:v1.10.0     "/bin/sh -c 'sh star…"   5 seconds ago   Up 3 seconds   0.0.0.0:10000->10000/tcp, :::10000->10000/tcp, 0.0.0.0:10105->10105/tcp, :::10105->10105/tcp   eager_driscoll
```

6.管理EventMesh容器   
在成功的运行了 EventMesh 容器后，你可以通过进入容器、查看日志、删除容器等方式管理容器。
**进入容器**命令示例:  
```
sudo docker exec -it [your container id or name] /bin/bash
```

在容器中**查看日志**命令示例:  
```
cd ../logs
tail -f eventmesh.out
```

**删除容器**命令示例:
```
sudo docker rm -f [your container id or name]
```

### eventmesh-sdk-java demo  
> EventMesh-sdk-java作为客户端，与eventmesh-runtime通信，用于完成消息的发送和接收。  
> EventMesh-sdk-java支持异步消息和广播消息。异步消息表示生产者只发送消息，不关心回复消息。广播消息表示生产者发送一次消息，所有订阅广播主题的消费者都将收到消息  
> EventMesh-sdk-java支持HTTP，TCP 和 GRPC 协议。  

TCP, HTTP 和 GRPC 示例都在eventmesh-examples模块下

#### 1.TCP  

##### 1.1 异步消息

- 创建主题`TEST-TOPIC-TCP-ASYNC`，可以通过`rocketmq-console`或者`rocketmq tools`命令
- 启动消费者，订阅上一步骤已经创建的Topic
```
运行 org.apache.eventmesh.tcp.demo.sub.eventmeshmessage.AsyncSubscribe 的main方法
```

- 启动发送端，发送消息
```
运行 org.apache.eventmesh.tcp.demo.pub.eventmeshmessage.AsyncPublish 的main方法
```

##### 1.2 广播消息

- 创建主题`TEST-TOPIC-TCP-BROADCAST`，可以通过`rocketmq-console`或者`rocketmq tools`命令
- 启动消费端，订阅上一步骤已经创建的Topic
```
运行 org.apache.eventmesh.tcp.demo.sub.eventmeshmessage.AsyncSubscribeBroadcast 的main方法
```

- 启动发送端，发送广播消息
````
运行 org.apache.eventmesh.tcp.demo.pub.eventmeshmessage.AsyncPublishBroadcast 的main方法
````

#### 2.HTTP

> 对于HTTP，eventmesh-sdk-java对对于异步事件实现了发送与订阅  
> 在演示中，Java类LiteMessage的content字段表示一个特殊的协议，因此，如果您要使用eventmesh-sdk-java的http-client，则只需设计协议的内容并在同一时间提供消费者的应用程序。  

##### 2.1 异步事件

> 生产者将事件发送给下游即可，无需等待响应  

- 创建主题`TEST-TOPIC-HTTP-ASYNC`，可以通过`rocketmq-console`或者`rocketmq tools`命令
- 启动消费端，订阅Topic  
  异步事件消费端为spring boot demo，运行demo即可启动服务并完成Topic订阅
```
运行 org.apache.eventmesh.http.demo.sub.SpringBootDemoApplication 的main方法
```

- 启动发送端，发送消息  
```
运行 org.apache.eventmesh.http.demo.pub.eventmeshmessage.AsyncPublishInstance 的main方法
```

#### 3.GRPC

> eventmesh-sdk-java 实现了 gRPC 协议. 它能异步和同步发送事件到 eventmesh-runtime. 它可以通过webhook和事件流方式订阅消费事件， 同时也支持 CNCF CloudEvents 协议.  

##### 3.1 异步事件发送 和 webhook订阅 

> `Async生产者`异步发送事件到`eventmesh-runtime`, 不需要等待事件储存到`event-store`在`webhook`消费者, 事件推送到消费者的`http endpoint url`。这个URL在消费者的`Subscription`模型定于. 这方法跟前面的`Http eventmsh client`类似。  

- 在rocketmq 创建主题`TEST-TOPIC-GRPC-ASYNC`  
- 启动 publisher 发送事件  
```
运行 org.apache.eventmesh.grpc.pub.eventmeshmessage.AsyncPublishInstance 的main方法
```

- 启动webhook消费者  
```
运行 org.apache.eventmesh.grpc.sub.app.SpringBootDemoApplication 的main方法
```

##### 3.2 同步事件发送和事件流订阅  

> `同步生产者`发送事件到`eventmesh-runtime`, 同时等待事件储存到`event-store`在事件流消费者，事件以流的形式推送到`ReceiveMsgHook`客户端。 这方法类似`eventmesh client`.   

- 在rocketmq 创建主题`TEST-TOPIC-GRPC-RR`
- 启动`Request-Reply publisher`发送事件  
```
运行 org.apache.eventmesh.grpc.pub.eventmeshmessage.RequestReplyInstance 的main方法
```

- 启动`stream subscriber`
```
运行 org.apache.eventmesh.grpc.sub.EventmeshAsyncSubscribe 的main方法
```

##### 3.3 批量事件发布  

> 批量发布多个事件到 eventmesh-runtime. 这是异步操作  

- 在rocketmq创建主题`TEST-TOPIC-GRPC-ASYNC`
- 启动 publisher 来批量发布事件
```
运行 org.apache.eventmesh.grpc.pub.eventmeshmessage.BatchPublishInstance 的main方法
```

#### 4.测试  

完成[store](#部署eventmesh-store)和[runtime](#部署eventmesh-runtime)的部署后，就可以在`eventmesh-examples`模块下运行我们的`demo`来体验`eventmesh`了!  

gradle编译：
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

打开对应log文件查看日志:  
```
cd /root/apache-eventmesh-1.10.0-src/eventmesh-examples/dist/logs
tail -f demo_tcp_pub.out
```

TCP Pub  
```
bash tcp_pub_eventmeshmessage.sh
```

打开对应log文件查看日志:  
```
cd /root/apache-eventmesh-1.10.0-src/eventmesh-examples/dist/logs
tail -f demo_tcp_sub.out
```

##### 4.2 TCP Broadcast 

TCP Sub Broadcast  
```
sh tcp_sub_eventmeshmessage_broadcast.sh
```

打开对应log文件查看日志:  
```
cd /root/apache-eventmesh-1.10.0-src/eventmesh-examples/dist/logs
tail -f demo_tcp_sub_broadcast.out
```

TCP Pub Broadcast  
```
sh tcp_pub_eventmeshmessage_broadcast.sh
```

打开对应log文件查看日志:  
```
cd /root/apache-eventmesh-1.10.0-src/eventmesh-examples/dist/logs
tail -f demo_tcp_pub_broadcast.out
```

##### 4.3 HTTP 

HTTP Sub  
```
sh http_sub.sh
```

打开对应log文件查看日志:  
```
cd /root/apache-eventmesh-1.10.0-src/eventmesh-examples/dist/logs
tail -f demo_http_sub.out
```

HTTP Pub
```
sh http_pub_eventmeshmessage.sh
```

打开对应log文件查看日志:  
```
cd /root/apache-eventmesh-1.10.0-src/eventmesh-examples/dist/logs
tail -f demo_http_pub.out
```

你可以在`/logs`目录下面看到不同模式的运行日志。

### 运行EventMesh-Operator

准备:  
```
docker
golang (version 1.19)
kubernetes (kubectl)
kubernetes和docker之间有一定的兼容性，请检查它们之间的版本兼容性，并下载相应的版本，以确保它们能一起正常工作。
```

#### 本地源码运行

1.启动:  

进入eventmesh-operator目录。
```
cd eventmesh-operator
```

将CRD安装到k8s集群。  
```
make install

# Uninstall CRDs from the K8s cluster
make uninstall
```

如果出现错误`eventmesh-operator/bin/controller-gen: No such file or directory` 
运行以下命令:  
```
# 如有必要，在本地下载controller-gen.
make controller-gen
# 如有必要，在本地下载kustomize.
make kustomize
```

查看crds信息:  
```
# 运行以下命令查看 crds 信息:
kubectl get crds
NAME                                      CREATED AT
connectors.eventmesh-operator.eventmesh   2023-11-28T01:35:21Z
runtimes.eventmesh-operator.eventmesh     2023-11-28T01:35:21Z
```

运行eventmesh-operator:  
```
# run controller
make run

# 成功启动pod后，运行以下命令查看pod。
kubectl get pods
NAME                      READY   STATUS    RESTARTS   AGE
connector-rocketmq-0      1/1     Running   0          12m
eventmesh-runtime-0-a-0   1/1     Running   0          12m
```

2.创建和删除CRs:  
自定义资源对象位于: `/config/samples`  
删除CR，只需将`create`替换为`delete`即可。  
```
# 为eventmesh-runtime、eventmesh-connector-rocketmq创建CR,创建clusterIP可让eventmesh-runtime与其他组件通信。
make create

#success:
configmap/runtime-config created
runtime.eventmesh-operator.eventmesh/eventmesh-runtime created
service/runtime-cluster-service created
configmap/connector-rocketmq-config created
connectors.eventmesh-operator.eventmesh/connector-rocketmq created

# 查看创建的service.
kubectl get service
NAME                      TYPE        CLUSTER-IP      EXTERNAL-IP   PORT(S)     AGE
runtime-cluster-service   ClusterIP   10.109.209.72   <none>        10000/TCP   17s

# 运行以下命令查看pod.
kubectl get pods
NAME                      READY   STATUS    RESTARTS   AGE
connector-rocketmq-0      1/1     Running   0          12m
eventmesh-runtime-0-a-0   1/1     Running   0          12m

# 删除CR
make delete
```

## 贡献

每个贡献者在推动 Apache EventMesh 的健康发展中都发挥了重要作用。我们真诚感谢所有为代码和文档作出贡献的贡献者。

- [贡献指南](https://eventmesh.apache.org/community/contribute/contribute)
- [Good First Issues](https://github.com/apache/eventmesh/issues?q=is%3Aopen+is%3Aissue+label%3A%22good+first+issue%22)

这里是[贡献者列表](https://github.com/apache/eventmesh/graphs/contributors)，感谢大家！ :)

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

Apache EventMesh 的开源协议遵循 [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.html).

## Community

| 微信小助手                                              | 微信公众号                                             | Slack                                                        |
| ------------------------------------------------------- | ------------------------------------------------------ | ------------------------------------------------------------ |
| <img src="resources/wechat-assistant.jpg" width="128"/> | <img src="resources/wechat-official.jpg" width="128"/> | [加入 Slack ](https://join.slack.com/t/apacheeventmesh/shared_invite/zt-1t1816dli-I0t3OE~IpdYWrZbIWhMbXg) |

双周会议 : [#Tencent meeting](https://meeting.tencent.com/dm/wes6Erb9ioVV) : 346-6926-0133

双周会议记录 : [bilibili](https://space.bilibili.com/1057662180)

### 邮件名单

| 名称    | 描述                              | 订阅                                                  | 取消订阅                                                    | 邮件列表存档                                                 |
| ------- | --------------------------------- | ----------------------------------------------------- | ----------------------------------------------------------- | ------------------------------------------------------------ |
| 用户    | 用户支持与用户问题                | [订阅](mailto:users-subscribe@eventmesh.apache.org)   | [取消订阅](mailto:users-unsubscribe@eventmesh.apache.org)   | [邮件存档](https://lists.apache.org/list.html?users@eventmesh.apache.org) |
| 开发    | 开发相关 (设计文档， Issues等等.) | [订阅](mailto:dev-subscribe@eventmesh.apache.org)     | [取消订阅](mailto:dev-unsubscribe@eventmesh.apache.org)     | [邮件存档](https://lists.apache.org/list.html?dev@eventmesh.apache.org) |
| Commits | 所有与仓库相关的 commits 信息通知 | [订阅](mailto:commits-subscribe@eventmesh.apache.org) | [取消订阅](mailto:commits-unsubscribe@eventmesh.apache.org) | [邮件存档](https://lists.apache.org/list.html?commits@eventmesh.apache.org) |
| Issues  | Issues 或者 PR 提交和代码Review   | [订阅](mailto:issues-subscribe@eventmesh.apache.org)  | [取消订阅](mailto:issues-unsubscribe@eventmesh.apache.org)  | [邮件存档](https://lists.apache.org/list.html?issues@eventmesh.apache.org) |

