[![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.apache.eventmesh/eventmesh-sdk-java/badge.svg)](https://maven-badges.herokuapp.com/maven-central/org.apache.eventmesh/eventmesh-sdk-java)

# 如何运行 eventmesh-sdk-java 演示

> EventMesh-sdk-java作为客户端，与eventmesh-runtime通信，用于完成消息的发送和接收。
>
> EventMesh-sdk-java支持异步消息和广播消息。异步消息表示生产者只发送消息，不关心回复消息。广播消息表示生产者发送一次消息，所有订阅广播主题的消费者都将收到消息
>
> EventMesh-sdk-java支持HTTP和TCP协议。

TCP 和 HTTP 示例都在**eventmesh-example**模块下

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



