[![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.apache.eventmesh/eventmesh-sdk-java/badge.svg)](https://maven-badges.herokuapp.com/maven-central/org.apache.eventmesh/eventmesh-sdk-java)

# 如何运行 eventmesh-sdk-java 演示

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

> 对于 GRPC, eventmesh-sdk-java 实现了 Google gRPC 和 Google Protocol Buffers 协议. 它能异步和同步发送事件到 eventmesh-runtime.
> 它可以通过webhook和事件流方式订阅消费事件， 同时也支持Apache CloudEvents 协议.

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