# EventMesh SDK for Java

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.apache.eventmesh/eventmesh-sdk-java/badge.svg?style=for-the-badge)](https://maven-badges.herokuapp.com/maven-central/org.apache.eventmesh/eventmesh-sdk-java)

EventMesh SDK for Java is the client that communicates with EventMesh Runtime to send and receive messages. The SDK supports sending async message (the producer sends the message and doesn't wait for the response) and broadcast message (the producer sends the message and all subscribers of the topic will receive the message). The SDK supports TCP, HTTP, and gRPC protocols. The demo projects are located in the [`eventmesh-example`](https://github.com/apache/incubator-eventmesh/tree/master/eventmesh-examples) module.

## TCP Protocol

### Async Message

- Create topic TEST-TOPIC-TCP-ASYNC on rocketmq-console

- Start consumer, subscribe topic in previous step.

```
Run the main method of org.apache.eventmesh.tcp.demo.sub.eventmeshmessage.AsyncSubscribe
```

- Start producer, send message

```
Run the main method of org.apache.eventmesh.tcp.demo.pub.eventmeshmessage.AsyncPublish
```

### Broadcast Message

- Create topic TEST-TOPIC-TCP-BROADCAST on rocketmq-console

- Start consumer, subscribe topic in previous step.

```
Run the main method of org.apache.eventmesh.tcp.demo.sub.eventmeshmessage.AsyncSubscribeBroadcast
```

- Start producer, send broadcast message

```
Run the main method of org.apache.eventmesh.tcp.demo.pub.eventmeshmessage.AsyncPublishBroadcast
```

## HTTP Protocol

> As to HTTP, eventmesh-sdk-java implements  the pub and sub for async event .
>
> In the demo, the field of `content` of the java class `LiteMessage` represents a special protocal, so if you want to use http-client of eventmesh-sdk-java, you just need to design the content of protocol and supply the consumer application at the same time.

### Async Event

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

## gRPC Protocol

> eventmesh-sdk-java implements the gRPC transport protocol. It can send events to eventmesh-runtime asynchronously
> and synchronously (using request-reply). It can also subscribe to the events using webhook subscriber and stream subscriber.
> CNCF CloudEvents protocol is also supported in the demo.

### Async event publisher and webhook subscriber

> producer asynchronously send the event to eventmesh-runtime, and don't need to wait for the event is delivered to the `event-store` of the eventmesh runtime
> In webhook subscriber, event is delivered to the http endpoint url that is specified in the `Subscription` model. This is similar to the Http eventmesh client.

- Create topic TEST-TOPIC-GRPC-ASYNC on rocketmq-console
- start publisher to publish to the topic as the following:

```
Run the main method of org.apache.eventmesh.grpc.pub.eventmeshmessage.AsyncPublishInstance
```

- Start webhook subscriber as the following:

```
Run the main method of org.apache.eventmesh.grpc.sub.app.SpringBootDemoApplication
```

### Sync event publisher and stream subscriber

> producer synchronously send the event to eventmesh-runtime, and wait for the event is delivered to the `event-store` of the eventmesh runtime
> In stream subscriber, event is delivered to the `ReceiveMsgHook` client as serials of event streams. This is similar to the TCP eventmesh client.

- Create topic TEST-TOPIC-GRPC-RR on rocketmq-console
- start Request-Reply publisher to publish to the topic as the following:

```
Run the main method of org.apache.eventmesh.grpc.pub.eventmeshmessage.RequestReplyInstance
```

- Start stream subscriber as the following:

```
Run the main method of org.apache.eventmesh.grpc.sub.EventmeshAsyncSubscribe
```

### Batch async event publisher

> Batch event publisher can publish several events in a batch to the eventmesh-runtime. This is synchronous operation.

- Create topic TEST-TOPIC-GRPC-ASYNC on rocketmq-console
- start publisher to publish to the topic as the following:

```
Run the main method of org.apache.eventmesh.grpc.pub.eventmeshmessage.BatchPublishInstance
```
