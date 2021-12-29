[![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.apache.eventmesh/eventmesh-sdk-java/badge.svg)](https://maven-badges.herokuapp.com/maven-central/org.apache.eventmesh/eventmesh-sdk-java)
# How to run eventmesh-sdk-java demo

> EventMesh-sdk-java, as the client, communicated with eventmesh-runtime, used to complete the sending and receiving of message.
>
> Supports async msg and broadcast msg. Async msg means the producer just sends msg and does not care reply msg. Broadcast msg means the producer send msg once and all the consumer subscribed the broadcast topic will receive the msg.
>
> EventMesh-sdk-java supports the protocol of HTTP and TCP.

TCP demos and HTTP demos are both under the **eventmesh-example** module.

### 1. TCP DEMO

#### Async msg

- Create topic TEST-TOPIC-TCP-ASYNC on rocketmq-console
  
- Start consumer, subscribe topic in previous step.

```
Run the main method of org.apache.eventmesh.tcp.demo.sub.eventmeshmessage.AsyncSubscribe
```

- Start producer, send message

```
Run the main method of org.apache.eventmesh.tcp.demo.pub.eventmeshmessage.AsyncPublish
```

#### Broadcast msg

- Create topic TEST-TOPIC-TCP-BROADCAST on rocketmq-console

- Start consumer, subscribe topic in previous step.

```
Run the main method of org.apache.eventmesh.tcp.demo.sub.eventmeshmessage.AsyncSubscribeBroadcast
```

* Start producer, send broadcast message

```
Run the main method of org.apache.eventmesh.tcp.demo.pub.eventmeshmessage.AsyncPublishBroadcast
```

### 2. HTTP DEMO

> As to HTTP, eventmesh-sdk-java implements  the pub and sub for async event .
>
> In the demo, the field of `content` of the java class `LiteMessage` represents a special protocal, so if you want to use http-client of eventmesh-sdk-java, you just need to design the content of protocal and supply the consumer application at the same time.

#### Async event

> producer send the event to consumer and don't need waiting response msg from consumer

- Create topic TEST-TOPIC-HTTP-ASYNC on rocketmq-console

- Start consumer, subscribe topic

  Async consumer demo is a spring boot application demo, you can easily run this demo to start service and subscribe the
  topic.

```
Run the main method of org.apache.eventmesh.http.demo.sub.SpringBootDemoApplication
```

- Start producer, produce msg

```
Run the main method of org.apache.eventmesh.http.demo.pub.eventmeshmessage.AsyncPublishInstance
```

