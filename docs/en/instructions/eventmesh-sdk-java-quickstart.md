## How to run eventmesh-sdk-java demo

> Eventmesh-sdk-java , as the client, communicated with eventmesh-emesher, used to complete the sending and receiving of message.  
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
Run the main method of cn.webank.eventmesh.client.tcp.demo.SyncResponse
```



* start producer, send message

```
Run the main method of cn.webank.eventmesh.client.tcp.demo.SyncRequest
```



#### Async msg 

- create topic

```
sh runadmin.sh updateTopic  -c ${ClusterName} -t ${topic} -n ${namesrvAddr}
```



- start consumer ,subscribe topic in previous step. 

```
Run the main method of cn.webank.eventmesh.client.tcp.demo.AsyncSubscribe
```



start producer, send  message

```
Run the main method of cn.webank.eventmesh.client.tcp.demo.AsyncPublish
```



#### Broadcast msg 

- create topic

```
sh runadmin.sh updateTopic  -c ${ClusterName} -t ${topic} -n ${namesrvAddr}
```



- start consumer ,subscribe topic in previous step. 

```
Run the main method of cn.webank.eventmesh.client.tcp.demo.AsyncSubscribeBroadcast
```



* start producer, send broadcast message

```
Run the main method of cn.webank.eventmesh.client.tcp.demo.AsyncPublishBroadcast
```

### 2. HTTP DEMO

> As to http, eventmesh-sdk-java just implements  the sending of msg. And it already  supports sync msg and async msg.
>
> In the demo ,the field of `content` of the java class `LiteMessage` represents a special protocal, so if you want to use http-client of eventmesh-sdk-java, you just need to design the content of protocal and supply the consumer appliacation at the same time.



#### Sync msg

> send msg ,producer need waiting until receive the response msg of consumer

```
Run the main method of cn.webank.eventmesh.client.http.demo.SyncRequestInstance
```



> send msg,producer handles the reponse msg in callback

```
Run the main method ofcn.webank.eventmesh.client.http.demo.AsyncSyncRequestInstance
```



#### Async msg

```
Run the main method of cn.webank.eventmesh.client.http.demo.AsyncPublishInstance
```

