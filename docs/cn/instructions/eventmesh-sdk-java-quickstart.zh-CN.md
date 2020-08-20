##如何运行eventmesh-sdk-java演示

> Eventmesh-sdk-java作为客户端，与eventmesh-emesher通信，用于完成消息的发送和接收。
>
> Eventmesh-sdk-java支持同步消息，异步消息和广播消息。同步消息表示生产者发送消息，需要消费者提供响应消息；异步消息表示生产者只发送消息，不关心回复消息。广播消息表示生产者发送一次消息，所有订阅广播主题的消费者都将收到消息。味精
>
> Eventmesh-sdk-java支持HTTP和TCP协议。


### 1. TCP DEMO

####同步消息

- 创建主题

```
sh runadmin.sh updateTopic -c $ {ClusterName} -t $ {topic} -n $ {namesrvAddr}
```



*启动消费者，订阅上一步骤已经创建的Topic

```
运行com.webank.eventmesh.client.tcp.demo.SyncResponse的主要方法
```



启动发送端，发送消息

```
运行com.webank.eventmesh.client.tcp.demo.SyncRequest的主要方法
```



####异步消息

- 创建主题

```
sh runadmin.sh updateTopic -c $ {ClusterName} -t $ {topic} -n $ {namesrvAddr}
```



- 启动消费者，订阅上一步骤已经创建的Topic

```
运行com.webank.eventmesh.client.tcp.demo.AsyncSubscribe的主要方法
```



启动发送端，发送消息

```
运行com.webank.eventmesh.client.tcp.demo.AsyncPublish的主要方法
```



####广播消息

- 创建主题

```
sh runadmin.sh updateTopic -c $ {ClusterName} -t $ {topic} -n $ {namesrvAddr}
```



- 启动消费端，订阅上一步骤已经创建的Topic

```
运行com.webank.eventmesh.client.tcp.demo.AsyncSubscribeBroadcast的主要方法
```



*启动发送端，发送广播消息

```
运行com.webank.eventmesh.client.tcp.demo.AsyncPublishBroadcast的主要方法
```

### 2. HTTP演示

>对于http，eventmesh-sdk-java仅实现msg的发送。而且它已经支持同步味精和异步味精。
>
>在演示中，Java类`LiteMessage`的`content`字段表示一个特殊的协议，因此，如果您要使用eventmesh-sdk-java的http-client，则只需设计协议的内容并提供消费者的应用程序在同一时间。



####同步消息

>发送消息，生产者需要等到收到用户的响应消息

```
运行com.webank.eventmesh.client.http.demo.SyncRequestInstance的主要方法
```



>发送消息，生产者在回调中处理响应消息

```
运行com.webank.eventmesh.client.http.demo.AsyncSyncRequestInstance的主要方法
```



####异步消息

```
运行com.webank.eventmesh.client.http.demo.AsyncPublishInstance的主要方法
```