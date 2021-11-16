# gRPC Protocol Document In Eventmesh-Runtime

#### 1. protobuf

The `eventmesh-protocol-gprc` module contains the protobuf file of the evenmesh client. the protobuf file
is located as `/src/main/proto/eventmesh-client.proto`.

Run the gradle build to generate the gRPC codes. The generated codes are located at `/build/generated/source/proto/main`.

These generated grpc codes will be used in `eventmesh-sdk-java` module.

#### 2. data models

- message

The following is the message data model, used by `publish()`, `requestReply()` and `broadcast()` APIs.
  
```
message RequestHeader {
    string env = 1;
    string region = 2;
    string idc = 3;
    string ip = 4;
    string pid = 5;
    string sys = 6;
    string username = 7;
    string password = 8;
    string version = 9;
    string language = 10;
    string seqNum = 11;
}

message Message {
   RequestHeader header = 1;
   string productionGroup = 2;
   string topic = 3;
   string content = 4;
   string ttl = 5;
   string uniqueId = 6;
}

message Response {
   string respCode = 1;
   string respMsg = 2;
   string respTime = 3;
   string seqNum = 4;
}
```

- subscription

The following data model is used by `subscribe()` and `unsubscribe()` APIs.

```
message Subscription {
   RequestHeader header = 1;
   string consumerGroup = 2;

   message SubscriptionItem {
      string topic = 1;
      string mode = 2;
      string type = 3;
      string url = 4;
   }

   repeated SubscriptionItem subscriptionItems = 3;
}
```

- heartbeat

The following data model is used by `heartbeat()` API.

```
message Heartbeat {
  RequestHeader header = 1;
  string clientType = 2;
  string producerGroup = 3;
  string consumerGroup = 4;

  message HeartbeatItem {
     string topic = 1;
     string url = 2;
  }

  repeated HeartbeatItem heartbeatItems = 5;
}
```

#### 3. service operations

- event publisher service APIs

```
   # Async event publish
   rpc publish(Message) returns (Response);

   # Sync event publish
   rpc requestReply(Message) returns (Response);

   # event broadcast
   rpc broadcast(Message) returns (Response);
```

- event consumer service APIs

```
   # The subscribed event will be delivered by invoking the webhook url in the Subscription
   rpc subscribe(Subscription) returns (Response);

   # The subscribed event will be delivered through stream of Message
   rpc subscribeStream(Subscription) returns (stream Message);

   rpc unsubscribe(Subscription) returns (Response);
```

- client heartbeat service API

```
   rpc heartbeat(Heartbeat) returns (Response);
```