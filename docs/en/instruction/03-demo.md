# Run our demos

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.apache.eventmesh/eventmesh-sdk-java/badge.svg)](https://maven-badges.herokuapp.com/maven-central/org.apache.eventmesh/eventmesh-sdk-java)

> EventMesh-sdk-java as the client，and comminucate with eventmesh-runtime，to finish the message sub and pub
>
> EventMesh-sdk-java support both async and broadcast.
>
> EventMesh-sdk-java support HTTP, TCP and gRPC.

The test demos of TCP, HTTP 和 GRPC are in the module **eventmesh-examples**

## 1 TCP DEMO

### 1.1 ASYNC

- Start consumer to subscribe the topic (we have created the TEST-TOPIC-TCP-ASYNC by default, you can also create other topic to test)

```
Run the main method of org.apache.eventmesh.tcp.demo.sub.eventmeshmessage.AsyncSubscribe
```

- Start producer to publish async message

```
Run the main method of org.apache.eventmesh.tcp.demo.pub.eventmeshmessage.AsyncPublish
```

### 1.2 BROADCAST

- Start subscriber to subscribe the topic (we have created the TEST-TOPIC-TCP-BROADCAST by default, you can also create other topic to test)

```
Run the main method of org.apache.eventmesh.tcp.demo.sub.eventmeshmessage.AsyncSubscribeBroadcast
```

- Start publisher to publish async message

```
Run the main method of org.apache.eventmesh.tcp.demo.pub.eventmeshmessage.AsyncPublishBroadcast
```

More information about EventMesh-TCP, please refer to [EventMesh TCP](/docs/en/sdk-java/03-tcp.md)


## 2 HTTP DEMO


### 2.1 ASYNC

- The subscriber is a SpringBoot demo, so run this demo to start subscriber (we have created the topic TEST-TOPIC-HTTP-ASYNC by default, you can also create other topic to test)

```
Run the main method of org.apache.eventmesh.http.demo.sub.SpringBootDemoApplication
```

- Start publisher to publish message

```
Run the main method of org.apache.eventmesh.http.demo.pub.eventmeshmessage.AsyncPublishInstance
```
More information about EventMesh-HTTP, please refer to [EventMesh HTTP](/docs/en/sdk-java/02-http.md)

## 3 GRPC DEMO

### 3.1 ASYNC PUBLISH & WEBHOOK SUBSCRIBE </h4>

- Start publisher to publish message (we have created the topic TEST-TOPIC-GRPC-ASYNC by default, you can also create other topic to test)

```
Run the main method of org.apache.eventmesh.grpc.pub.eventmeshmessage.AsyncPublishInstance
```

- Start webhook subscriber

```
Run the main method of org.apache.eventmesh.grpc.sub.app.SpringBootDemoApplication
```

###  3.2 SYNC PUBLISH & STREAM SUBSCRIBE

- Start Request-Reply publisher to publish message (we have created the topic TEST-TOPIC-GRPC-RR by default, you can also create other topic to test)

```
Run the main method of org.apache.eventmesh.grpc.pub.eventmeshmessage.RequestReplyInstance
```

- Start stream subscriber

```
Run the main method of org.apache.eventmesh.grpc.sub.EventmeshAsyncSubscribe
```

### 3.3 PUBLISH BATCH MESSAGE

- Start publisher to publish batch message (we have created the TEST-TOPIC-GRPC-ASYNC by default, you can also create other topic to test)

```
Run the main method of org.apache.eventmesh.grpc.pub.eventmeshmessage.BatchPublishInstance
```

More information about EventMesh-gRPC, please refer to [EventMesh gRPC](/docs/en/sdk-java/04-grpc.md)

## 4 Run these demos by yourself

Please refer to [EventMesh Store](/docs/en/instruction/01-store.md) and [EventMesh Runtime](/docs/en/instruction/02-runtime.md) to finish the necessary deployment before try our demo

After finishing the deployment of store and runtime, you can run our demos in module `eventmesh-examples`:

### TCP Sub

  ```shell
  cd bin
  sh tcp_eventmeshmessage_sub.sh
  ```

### TCP Pub

  ```shell
  cd bin
  sh tcp_pub_eventmeshmessage.sh
  ```

### TCP Sub Broadcast

  ```shell
  cd bin
  sh tcp_sub_eventmeshmessage_broadcast.sh
  ```

### TCP Pub Broadcast

  ```shell
  cd bin
  sh tcp_pub_eventmeshmessage_broadcast.sh
  ```

### HTTP Sub

  ```shell
  cd bin
  sh http_sub.sh
  ```

### HTTP Pub

  ```shell
  cd bin
  sh http_pub_eventmeshmessage.sh
  ```

You can review the log in the folder `/logs`
