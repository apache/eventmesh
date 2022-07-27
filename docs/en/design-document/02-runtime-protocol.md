# EventMesh Runtime Protocol

## TCP Protocol

### Protocol Format

|Name|Size|Description|
|-|-|-|
|Magic Code|9 bytes|Default: `EventMesh`|
|Protocol Version|4 bytes|Default: `0000`|
|Message Size|4 bytes|The total length of the message|
|Header Size|4 bytes|The length of the message header|
|Message Body||The content of the message|

### Message Object in the Business Logic Layer

#### Message Composition

The `Package` class in the [`Package.java` file](https://github.com/apache/incubator-eventmesh/blob/master/eventmesh-common/src/main/java/org/apache/eventmesh/common/protocol/tcp/Package.java) is the TCP message object used in business logic layer. The class contains the `header` and `body` fields.

```java
public class Package {
   private Header header;
   private Object body;
}

public class Header {
   private Command cmd;
   private int code;
   private String msg;
   private String seq;
}
```

#### Specification

- Message Header (the `header` field): The `cmd` field in the `Header` class specifies the different types of messages.
- Message Body (the `body` field): The type of the message body should be defined based on `cmd` field in the `Header` class.

|Command|Type of Body|
|-|-|
| HEARTBEAT_REQUEST, HEARTBEAT_RESPONSE, HELLO_RESPONSE, CLIENT_GOODBYE_REQUEST, CLIENT_GOODBYE_RESPONSE, SERVER_GOODBYE_REQUEST, SERVER_GOODBYE_RESPONSE, LISTEN_REQUEST, LISTEN_RESPONSE, UNSUBSCRIBE_REQUEST, SUBSCRIBE_RESPONSE, UNSUBSCRIBE_RESPONSE, ASYNC_MESSAGE_TO_SERVER_ACK, BROADCAST_MESSAGE_TO_SERVER_ACK|N/A|
|HELLO_REQUEST|UserAgent|
|SUBSCRIBE_REQUEST|Subscription|
| REQUEST_TO_SERVER, REQUEST_TO_CLIENT, RESPONSE_TO_SERVER, RESPONSE_TO_CLIENT, ASYNC_MESSAGE_TO_SERVER, ASYNC_MESSAGE_TO_CLIENT, BROADCAST_MESSAGE_TO_SERVER, BROADCAST_MESSAGE_TO_CLIENT, ASYNC_MESSAGE_TO_CLIENT_ACK, BROADCAST_MESSAGE_TO_CLIENT_ACK, RESPONSE_TO_CLIENT_ACK, REQUEST_TO_CLIENT_ACK|OpenMessage|
|REDIRECT_TO_CLIENT|RedirectInfo|

### Example of Client-Server Interaction

```java
public enum Command {
    // Heartbeat
    HEARTBEAT_REQUEST(0),                              // Client send heartbeat request to server
    HEARTBEAT_RESPONSE(1),                             // Server reply heartbeat response to client

    // Hello
    HELLO_REQUEST(2),                                  // Client send connect request to server
    HELLO_RESPONSE(3),                                 // Server reply connect response to client

    // Disconncet
    CLIENT_GOODBYE_REQUEST(4),                         // Client send disconnect request to server
    CLIENT_GOODBYE_RESPONSE(5),                        // Server reply disconnect response to client
    SERVER_GOODBYE_REQUEST(6),                         // Server send disconncet request to client
    SERVER_GOODBYE_RESPONSE(7),                        // Client reply disconnect response to server

    // Subscribe and UnSubscribe
    SUBSCRIBE_REQUEST(8),                              // Slient send subscribe request to server
    SUBSCRIBE_RESPONSE(9),                             // Server reply subscribe response to client
    UNSUBSCRIBE_REQUEST(10),                           // Client send unsubscribe request to server
    UNSUBSCRIBE_RESPONSE(11),                          // Server reply unsubscribe response to client

    // Listen
    LISTEN_REQUEST(12),                                // Client send listen request to server
    LISTEN_RESPONSE(13),                               // Server reply listen response to client

    // Send sync message
    REQUEST_TO_SERVER(14),                             // Client (Producer) send sync message to server
    REQUEST_TO_CLIENT(15),                             // Server push sync message to client(Consumer)
    REQUEST_TO_CLIENT_ACK(16),                         // Client (Consumer) send ack of sync message to server
    RESPONSE_TO_SERVER(17),                            // Client (Consumer) send reply message to server
    RESPONSE_TO_CLIENT(18),                            // Server push reply message to client(Producer)
    RESPONSE_TO_CLIENT_ACK(19),                        // Client (Producer) send acknowledgement of reply message to server

    // Send async message
    ASYNC_MESSAGE_TO_SERVER(20),                       // Client send async msg to server
    ASYNC_MESSAGE_TO_SERVER_ACK(21),                   // Server reply ack of async msg to client
    ASYNC_MESSAGE_TO_CLIENT(22),                       // Server push async msg to client
    ASYNC_MESSAGE_TO_CLIENT_ACK(23),                   // Client reply ack of async msg to server

    // Send broadcast message
    BROADCAST_MESSAGE_TO_SERVER(24),                   // Client send broadcast msg to server
    BROADCAST_MESSAGE_TO_SERVER_ACK(25),               // Server reply ack of broadcast msg to client
    BROADCAST_MESSAGE_TO_CLIENT(26),                   // Server push broadcast msg to client
    BROADCAST_MESSAGE_TO_CLIENT_ACK(27),               // Client reply ack of broadcast msg to server

    // Redirect
    REDIRECT_TO_CLIENT(30),                            // Server send redirect instruction to client
}
```

### Client-Initiated Interaction

|Scene|Client Request|Server Response|
|-|-|-|
| Hello           | HELLO_REQUEST                | HELLO_RESPONSE                  |      |
| Heartbeat           | HEARTBEAT_REQUEST            | HEARTBEAT_RESPONSE              |      |
| Subscribe           | SUBSCRIBE_REQUEST            | SUBSCRIBE_RESPONSE              |      |
| Unsubscribe       | UNSUBSCRIBE_REQUEST          | UNSUBSCRIBE_RESPONSE            |      |
| Listen   | LISTEN_REQUEST               | LISTEN_RESPONSE                 |      |
| Send sync message     | REQUEST_TO_SERVER            | RESPONSE_TO_CLIENT              |      |
| Send the response of sync message| RESPONSE_TO_SERVER           | N/A                              |      |
| Send async message   | ASYNC_MESSAGE_TO_SERVER      | ASYNC_MESSAGE_TO_SERVER_ACK     |      |
| Send broadcast message   | BROADCAST_MESSAGE_TO_SERVER  | BROADCAST_MESSAGE_TO_SERVER_ACK |      |
| Client start to disconnect | CLIENT_GOODBYE_REQUEST       | CLIENT_GOODBYE_RESPONSE         |      |

### Server-Initiated Interaction

|Scene|Server Request|Client Response|Remark|
|-|-| ------------------------------- | ---- |
| Push sync message to client   | REQUEST_TO_CLIENT            | REQUEST_TO_CLIENT_ACK           |      |
| Push the response message of sync message to client   | RESPONSE_TO_CLIENT           | RESPONSE_TO_CLIENT_ACK          |      |
| Push async message to client | ASYNC_MESSAGE_TO_CLIENT      | ASYNC_MESSAGE_TO_CLIENT_ACK     |      |
| Push broadcast message to client | BROADCAST_MESSAGE_TO_CLIENT  | BROADCAST_MESSAGE_TO_CLIENT_ACK |      |
| Server start to disconnect     | SERVER_GOODBYE_REQUEST       | --                              |      |
| Server send redirect   | REDIRECT_TO_CLIENT           | --                              |      |

### Type of Message

#### Sync Message

![Sync Message](../../images/design-document/sync-message.png)

#### Async Message

![Async Message](../../images/design-document/async-message.png)

#### Boardcast Message

![Boardcast Message](../../images/design-document/broadcast-message.png)

## HTTP Protocol

### Protocol Format

The `EventMeshMessage` class in the  [`EventMeshMessage.java` file](https://github.com/apache/incubator-eventmesh/blob/master/eventmesh-common/src/main/java/org/apache/eventmesh/common/EventMeshMessage.java) is the HTTP message definition of EventMesh Runtime.

```java
public class EventMeshMessage {
    private String bizSeqNo;

    private String uniqueId;

    private String topic;

    private String content;

    private Map<String, String> prop;

    private final long createTime = System.currentTimeMillis();
}
```

### HTTP Post Request

#### Heartbeat Message

##### Request Header

| Key      | Description             |
| -------- | ---------------- |
| Env      | Enviroment of Client   |
| Region   | Region of Client  |
| Idc      | IDC of Client    |
| Dcn      | DCN of Client    |
| Sys      | Subsystem ID of Client |
| Pid      | Client Process ID     |
| Ip       | Client Ip        |
| Username | Client username    |
| Passwd   | Client password      |
| Version  | Protocol version        |
| Language | Develop language         |
| Code     | Request Code           |

##### Request Body

|Key|Description|
|-|-|
|`clientType`|`ClientType.PUB` for Producer, `ClientType.SUB` for Consumer|
|`heartbeatEntities`|Topic, URL, etc.|

#### Subscribe Message

##### Request Header

The request header of the Subscribe message is identical to the request header of the Heartbeat message.

##### Request Body

|Key|Description|
|-|-|
|`topic`|The topic that the client requested to subscribe to|
|`url`|The callback URL of the client|

#### Unsubscribe Message

##### Request Header

The request header of the Unsubscribe message is identical to the request header of the Heartbeat message.

##### Request Body

The request body of the Unsubscribe message is identical to the request body of the Subscribe message.

#### Send Async Message

##### Request Header

The request header of the Send Async message is identical to the request header of the Heartbeat message.

##### Request Body

|Key|Description|
|-|-|
|`topic`|Topic of the message|
|`content`|The content of the message|
|`ttl`|The time-to-live of the message|
|`bizSeqNo`|The biz sequence number of the message|
|`uniqueId`|The unique ID of the message|

### Client-Initiated Interaction

|Scene|Client Request|Server Response|Remark|
|-|-|-|-|
| Heartbeat         | HEARTBEAT(203)               | SUCCESS(0) or EVENTMESH_HEARTBEAT_ERROR(19)    |      |
| Subscribe         | SUBSCRIBE(206)               | SUCCESS(0) or EVENTMESH_SUBSCRIBE_ERROR(17)    |      |
| Unsubscribe     | UNSUBSCRIBE(207)             | SUCCESS(0) or EVENTMESH_UNSUBSCRIBE_ERROR(18)  |      |
| Send async message | MSG_SEND_ASYNC(104)          | SUCCESS(0) or EVENTMESH_SEND_ASYNC_MSG_ERR(14) |      |

### Server-Initiated Interaction

|Scene|Client Request|Server Response|Remark|
|-|-|-|-|
|Push async message to the client|HTTP_PUSH_CLIENT_ASYNC(105)|`retCode`|The push is successful if the `retCode` is `0`|

## gRPC Protocol

### Protobuf

The `eventmesh-protocol-gprc` module contains the [protobuf definition file](https://github.com/apache/incubator-eventmesh/blob/master/eventmesh-protocol-plugin/eventmesh-protocol-grpc/src/main/proto/eventmesh-client.proto) of the Evenmesh client. The `gradle build` command generates the gRPC codes, which are located in `/build/generated/source/proto/main`. The generated gRPC codes are used in `eventmesh-sdk-java` module.

### Data Model

#### Message

The message data model used by `publish()`, `requestReply()` and `broadcast()` APIs is defined as:

```protobuf
message RequestHeader {
    string env = 1;
    string region = 2;
    string idc = 3;
    string ip = 4;
    string pid = 5;
    string sys = 6;
    string username = 7;
    string password = 8;
    string language = 9;
    string protocolType = 10;
    string protocolVersion = 11;
    string protocolDesc = 12;
}

message SimpleMessage {
   RequestHeader header = 1;
   string producerGroup = 2;
   string topic = 3;
   string content = 4;
   string ttl = 5;
   string uniqueId = 6;
   string seqNum = 7;
   string tag = 8;
   map<string, string> properties = 9;
}

message BatchMessage {
   RequestHeader header = 1;
   string producerGroup = 2;
   string topic = 3;

   message MessageItem {
      string content = 1;
      string ttl = 2;
      string uniqueId = 3;
      string seqNum = 4;
      string tag = 5;
      map<string, string> properties = 6;
   }

   repeated MessageItem messageItem = 4;
}

message Response {
   string respCode = 1;
   string respMsg = 2;
   string respTime = 3;
}
```

#### Subscription

The subscription data model used by `subscribe()` and `unsubscribe()` APIs is defined as:

```protobuf
message Subscription {
   RequestHeader header = 1;
   string consumerGroup = 2;

   message SubscriptionItem {
      enum SubscriptionMode {
         CLUSTERING = 0;
         BROADCASTING = 1;
      }

      enum SubscriptionType {
         ASYNC = 0;
         SYNC = 1;
      }

      string topic = 1;
      SubscriptionMode mode = 2;
      SubscriptionType type = 3;
   }

   repeated SubscriptionItem subscriptionItems = 3;
   string url = 4;
}
```

#### Heartbeat

The heartbeat data model used by the `heartbeat()` API is defined as:

```protobuf
message Heartbeat {
  enum ClientType {
     PUB = 0;
     SUB = 1;
  }

  RequestHeader header = 1;
  ClientType clientType = 2;
  string producerGroup = 3;
  string consumerGroup = 4;

  message HeartbeatItem {
     string topic = 1;
     string url = 2;
  }

  repeated HeartbeatItem heartbeatItems = 5;
}
```

### Service Definition

#### Event Publisher Service

```protobuf
service PublisherService {
   // Async event publish
   rpc publish(SimpleMessage) returns (Response);

   // Sync event publish
   rpc requestReply(SimpleMessage) returns (Response);

   // Batch event publish
   rpc batchPublish(BatchMessage) returns (Response);
}
```

#### Event Consumer Service

```protobuf
service ConsumerService {
   // The subscribed event will be delivered by invoking the webhook url in the Subscription
   rpc subscribe(Subscription) returns (Response);

   // The subscribed event will be delivered through stream of Message
   rpc subscribeStream(Subscription) returns (stream SimpleMessage);

   rpc unsubscribe(Subscription) returns (Response);
}
```

#### Client Hearthbeat Service

```protobuf
service HeartbeatService {
   rpc heartbeat(Heartbeat) returns (Response);
}
```
