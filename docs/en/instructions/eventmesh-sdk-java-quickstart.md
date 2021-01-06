## How to run eventmesh-sdk-java demo

> Eventmesh-sdk-java , as the client, communicated with eventmesh-runtime, used to complete the sending and receiving of message.  
>
> Eventmesh-sdk-java supports sync msg, async msg and broadcast msg. Sync msg means the producer sends msg which need the consumer supplies the response msg, Async msg means the producer just sends msg and does not care reply msg.Broadcast msg means the producer send msg once and all the consumer subscribed the broadcast topic will receive the msg. 
>
> Eventmesh-sdk-java supports  the protocol  of HTTP and TCP.  


###  1. TCP DEMO

#### Sync msg 

- create topic

```
sh runadmin.sh updateTopic  -c ${ClusterName} -t ${topic} -n ${namesrvAddr}
```



* start consumer ,subscribe topic in previous step. 

```
Run the main method of com.webank.eventmesh.client.tcp.demo.SyncResponse
```



* start producer, send message

```
Run the main method of com.webank.eventmesh.client.tcp.demo.SyncRequest
```



#### Async msg 

- create topic

```
sh runadmin.sh updateTopic  -c ${ClusterName} -t ${topic} -n ${namesrvAddr}
```



- start consumer ,subscribe topic in previous step. 

```
Run the main method of com.webank.eventmesh.client.tcp.demo.AsyncSubscribe
```



start producer, send  message

```
Run the main method of com.webank.eventmesh.client.tcp.demo.AsyncPublish
```



#### Broadcast msg 

- create topic

```
sh runadmin.sh updateTopic  -c ${ClusterName} -t ${topic} -n ${namesrvAddr}
```



- start consumer ,subscribe topic in previous step. 

```
Run the main method of com.webank.eventmesh.client.tcp.demo.AsyncSubscribeBroadcast
```



* start producer, send broadcast message

```
Run the main method of com.webank.eventmesh.client.tcp.demo.AsyncPublishBroadcast
```

### 2. HTTP DEMO

> As to http, eventmesh-sdk-java implements  the sending of sync msg. For async event  it implements the pub and sub. 
>
> In the demo ,the field of `content` of the java class `LiteMessage` represents a special protocal, so if you want to use http-client of eventmesh-sdk-java, you just need to design the content of protocal and supply the consumer appliacation at the same time.



#### Sync msg

> send msg ,producer need waiting until receive the response msg of consumer

```
Run the main method of com.webank.eventmesh.client.http.demo.SyncRequestInstance
```



> send msg,producer handles the reponse msg in callback

```
Run the main method of com.webank.eventmesh.client.http.demo.AsyncSyncRequestInstance
```



#### Async event

> producer send the event to consumer and don't need waiting response msg of consumer

- start consumer, subscribe topic

Async consumer demo is a spring boot application demo,  you can easily run this demo to start service and subscribe the topic.

```
Run the main method of com.webank.eventmesh.client.http.demo.sub.SpringBootDemoApplication
```

- start producer, produce msg

```
Run the main method of com.webank.eventmesh.client.http.demo.AsyncPublishInstance
```

