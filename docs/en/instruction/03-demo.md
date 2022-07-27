# 如何运行 eventmesh-sdk-java 演示

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.apache.eventmesh/eventmesh-sdk-java/badge.svg)](https://maven-badges.herokuapp.com/maven-central/org.apache.eventmesh/eventmesh-sdk-java)

> EventMesh-sdk-java作为客户端，与eventmesh-runtime通信，用于完成消息的发送和接收。
>
> EventMesh-sdk-java支持异步消息和广播消息。异步消息表示生产者只发送消息，不关心回复消息。广播消息表示生产者发送一次消息，所有订阅广播主题的消费者都将收到消息
>
> EventMesh-sdk-java支持HTTP，TCP 和 GRPC 协议。

TCP, HTTP 和 GRPC 示例都在**eventmesh-example**模块下

### 1. TCP DEMO

<h4>异步消息</h4>

- 创建主题TEST-TOPIC-TCP-ASYNC，可以通过rocketmq-console或者rocketmq tools 命令

- 启动消费者，订阅上一步骤已经创建的Topic

```
运行org.apache.eventmesh.tcp.demo.sub.eventmeshmessage.AsyncSubscribe的主要方法
```

- 启动发送端，发送消息

```
运行org.apache.eventmesh.tcp.demo.pub.eventmeshmessage.AsyncPublish的主要方法
```

<h4>广播消息</h4>

- 创建主题TEST-TOPIC-TCP-BROADCAST，可以通过rocketmq-console或者rocketmq tools 命令

- 启动消费端，订阅上一步骤已经创建的Topic

```
运行org.apache.eventmesh.tcp.demo.sub.eventmeshmessage.AsyncSubscribeBroadcast的主要方法
```

- 启动发送端，发送广播消息

```
运行org.apache.eventmesh.tcp.demo.pub.eventmeshmessage.AsyncPublishBroadcast的主要方法
```

### 2. HTTP演示

> 对于HTTP，eventmesh-sdk-java对对于异步事件实现了发送与订阅
>
>在演示中，Java类`LiteMessage`的`content`字段表示一个特殊的协议，因此，如果您要使用eventmesh-sdk-java的http-client，则只需设计协议的内容并在同一时间提供消费者的应用程序。

<h4>异步事件</h4>

> 生产者将事件发送给下游即可，无需等待响应

- 创建主题TEST-TOPIC-HTTP-ASYNC，可以通过rocketmq-console或者rocketmq tools 命令

- 启动消费端，订阅Topic

  异步事件消费端为spring boot demo，运行demo即可启动服务并完成Topic订阅

```
运行org.apache.eventmesh.http.demo.sub.SpringBootDemoApplication的主要方法
```

- 启动发送端，发送消息

```
运行org.apache.eventmesh.http.demo.pub.eventmeshmessage.AsyncPublishInstance的主要方法
```

### 3. GRPC 演示

> eventmesh-sdk-java 实现了 gRPC 协议. 它能异步和同步发送事件到 eventmesh-runtime.
> 它可以通过webhook和事件流方式订阅消费事件， 同时也支持 CNCF CloudEvents 协议.

<h4> 异步事件发送 和 webhook订阅 </h4>

> Async生产者 异步发送事件到 eventmesh-runtime, 不需要等待事件储存到 `event-store`
> 在webhook 消费者, 事件推送到消费者的http endpoint url。这个URL在消费者的 `Subscription` 模型定于. 这方法跟前面的Http eventmsh client类似。

- 在rocketmq 创建主题 TEST-TOPIC-GRPC-ASYNC
- 启动 publisher 发送事件

```
运行 org.apache.eventmesh.grpc.pub.eventmeshmessage.AsyncPublishInstance 的主要方法
```

- 启动 webhook 消费者

```
运行 org.apache.eventmesh.grpc.sub.app.SpringBootDemoApplication 的主要方法
```

<h4> 同步事件发送和事件流订阅 </h4>

> 同步生产者 发送事件到 eventmesh-runtime, 同时等待事件储存到 `event-store`
> 在事件流消费者，事件以流的形式推送到 `ReceiveMsgHook` 客户端。 这方法类似 eventmesh client.

- 在rocketmq 创建主题 TEST-TOPIC-GRPC-RR
- 启动 Request-Reply publisher 发送事件

```
运行 org.apache.eventmesh.grpc.pub.eventmeshmessage.RequestReplyInstance 的主要方法
```

- 启动 stream subscriber

```
运行 org.apache.eventmesh.grpc.sub.EventmeshAsyncSubscribe 的主要方法
```

<h4> 批量事件发布 </h4>

> 批量发布多个事件到 eventmesh-runtime. 这是异步操作

- 在rocketmq 创建主题 TEST-TOPIC-GRPC-ASYNC
- 启动 publisher 来批量发布事件

```
运行 org.apache.eventmesh.grpc.pub.eventmeshmessage.BatchPublishInstance 的主要方法
```









### 3.4 测试

**预先准备** ：RocketMQ Namesrv & Broker

你可以通过[这里](https://github.com/apache/rocketmq-docker)来构建rocketmq镜像或者从 docker hub上获取rocketmq镜像.

```shell
#获取namesrv镜像
docker pull rocketmqinc/rocketmq-namesrv:4.5.0-alpine
#获取broker镜像
docker pull rocketmqinc/rocketmq-broker:4.5.0-alpine

#运行namerv容器
docker run -d -p 9876:9876 -v `pwd` /data/namesrv/logs:/root/logs -v `pwd`/data/namesrv/store:/root/store --name rmqnamesrv  rocketmqinc/rocketmq-namesrv:4.5.0-alpine sh mqnamesrv

#运行broker容器
docker run -d -p 10911:10911 -p 10909:10909 -v `pwd`/data/broker/logs:/root/logs -v `pwd`/data/broker/store:/root/store --name rmqbroker --link rmqnamesrv:namesrv -e "NAMESRV_ADDR=namesrv:9876" rocketmqinc/rocketmq-broker:4.5.0-alpine sh mqbroker -c ../conf/broker.conf
```

这里 **rocketmq-broker ip** 是 **pod ip**, 如果你想修改这个ip, 可以通过挂载容器中 **broker.conf** 文件的方式并修改文件中的 **brokerIP1** 配置项为自定义值

**3.4.1 运行示例**

Windows

- Windows系统下运行示例可以参考[这里](https://github.com/apache/incubator-eventmesh/blob/develop/docs/cn/instructions/eventmesh-sdk-java-quickstart.zh-CN.md)

Linux

- **获取 eventmesh-test_1.3.0-release.tar.gz**

  你可以从我们的 **releases** 获取或者**通过源码的方式进行构建**

  **通过源码的方式进行构建**：

  ```shell
  cd /* Your Deploy Path */EventMesh/eventmesh-test
  gradle clean testdist testtar -x test`
  ```

  可以在 `/eventmesh-test/build` 目录下获得 **eventmesh-test_1.3.0-release.tar.gz**

- **修改配置文件**

  ```shell
  #上传
  upload eventmesh-test_1.3.0-release.tar.gz
  #解压
  tar -zxvf eventmesh-test_1.3.0-release.tar.gz
  #配置
  cd conf
  config your application.properties
  ```

- **运行**

  TCP Sub

  ```shell
  cd bin
  sh tcp_sub.sh
  ```

  TCP Pub

  ```shell
  cd bin
  sh tcp_pub.sh
  ```

  TCP Sub Broadcast

  ```shell
  cd bin
  sh tcp_sub_broadcast.sh
  ```

  TCP Pub Broadcast

  ```shell
  cd bin
  sh tcp_pub_broadcast.sh
  ```

  HTTP Sub

  ```shell
  cd bin
  sh http_sub.sh
  ```

  HTTP Pub

  ```shell
  cd bin
  sh http_pub.sh
  ```

  之后 , 你可以在 `/logs` 目录下面看到不同模式的运行日志
