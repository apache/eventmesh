grpcurl
===

install
---
```
go install github.com/fullstorydev/grpcurl/cmd/grpcurl@latest
```

list api
---
```
grpcurl.exe -plaintext localhost:10010 list
eventmesh.common.protocol.grpc.ConsumerService
eventmesh.common.protocol.grpc.HeartbeatService
eventmesh.common.protocol.grpc.PublisherService
grpc.reflection.v1alpha.ServerReflection
```

list service
---
```
grpcurl.exe -plaintext localhost:10010 list eventmesh.common.protocol.grpc.ConsumerService
eventmesh.common.protocol.grpc.ConsumerService.subscribe
eventmesh.common.protocol.grpc.ConsumerService.subscribeStream
eventmesh.common.protocol.grpc.ConsumerService.unsubscribe
```

describe service
---
```
grpcurl.exe -plaintext localhost:10010 describe eventmesh.common.protocol.grpc.PublisherService
eventmesh.common.protocol.grpc.PublisherService is a service:
service PublisherService {
  rpc batchPublish ( .eventmesh.common.protocol.grpc.BatchMessage ) returns ( .eventmesh.common.protocol.grpc.Response );
  rpc publish ( .eventmesh.common.protocol.grpc.SimpleMessage ) returns ( .eventmesh.common.protocol.grpc.Response );
  rpc requestReply ( .eventmesh.common.protocol.grpc.SimpleMessage ) returns ( .eventmesh.common.protocol.grpc.SimpleMessage );
}
```

invoke service
---
### subscribe
```
grpcurl.exe -plaintext -d '
{
    "header": {
        "env": "11",
        "region": "sh",
        "idc": "test-idc",
        "ip": "169.254.45.15",
        "pid": "12345",
        "sys": "test",
        "username": "username",
        "password": "password",
        "language": "go",
        "protocolType": "cloudevents",
        "protocolVersion": "1.0",
        "protocolDesc": "nil"
    },
    "subscriptionItems": [
        {
            "topic":"grpc-topic",
            "mode":"CLUSTERING",
            "type":"SYNC"
        }
    ],
    "consumerGroup": "test-grpc-group",
    "url": "http://127.0.0.1:18080"
}
' localhost:10010 eventmesh.common.protocol.grpc.ConsumerService/subscribe
```

### publish
```
grpcurl.exe -plaintext -d '
{
    "header": {
        "env": "env",
        "idc": "idc-test",
        "ip": "1.1.1.1",
        "pid": "1",
        "sys": "test",
        "username": "USERNAME",
        "password": "passwd",
        "language": "GO",
        "protocolType": "cloudevents",
        "protocolVersion": "1.0",
        "protocolDesc": "grpc"
    },
    "producerGroup": "test-producer-group",
    "topic": "grpc-topic",
    "content": "{\"specversion\":\"1.0\",\"id\":\"ee287b48-90fe-4c27-995e-b2da745a2a8b\",\"source\":\"/\",\"type\":\"cloudevents\",\"subject\":\"grpc-topic\",\"datacontenttype\":\"application/json\",\"data\":{\"msg\":\"msg from grpc go server\"},\"idc\":\"idc-test\",\"uniqueid\":\"1\",\"producergroup\":\"test-producer-group\",\"protocoldesc\":\"grpc\",\"seqnum\":\"1\",\"username\":\"USERNAME\",\"passwd\":\"passwd\",\"pid\":\"1\",\"sys\":\"test\",\"language\":\"GO\",\"protocoltype\":\"cloudevents\",\"env\":\"\",\"ip\":\"1.1.1.1\",\"protocolversion\":\"1.0\"}",
    "ttl": "10000",
    "uniqueId": "1",
    "seqNum": "1",
    "properties": {
        "contenttype": "application/json",
        "env": "",
        "idc": "idc-test",
        "ip": "1.1.1.1",
        "language": "GO",
        "passwd": "passwd",
        "pid": "1",
        "producergroup": "test-producer-group",
        "protocoldesc": "grpc",
        "protocoltype": "cloudevents",
        "protocolversion": "1.0",
        "seqnum": "1",
        "sys": "test",
        "uniqueid": "1",
        "username": "USERNAME"
    }
}
' localhost:10010 eventmesh.common.protocol.grpc.PublisherService/publish
```

### RequestReply
```
grpcurl.exe -plaintext -d '
{
    "header": {
        "env": "env",
        "idc": "idc-test",
        "ip": "1.1.1.1",
        "pid": "2",
        "sys": "test",
        "username": "USERNAME",
        "password": "passwd",
        "language": "GO",
        "protocolType": "cloudevents",
        "protocolVersion": "1.0",
        "protocolDesc": "grpc"
    },
    "producerGroup": "test-producer-group",
    "topic": "grpc-topic",
    "content": "{\"specversion\":\"1.0\",\"id\":\"ee287b48-90fe-4c27-995e-b2da745a2a82\",\"source\":\"/\",\"type\":\"cloudevents\",\"subject\":\"grpc-topic\",\"datacontenttype\":\"application/json\",\"data\":{\"msg\":\"msg from grpc go server\"},\"idc\":\"idc-test\",\"uniqueid\":\"1\",\"producergroup\":\"test-producer-group\",\"protocoldesc\":\"grpc\",\"seqnum\":\"1\",\"username\":\"USERNAME\",\"passwd\":\"passwd\",\"pid\":\"1\",\"sys\":\"test\",\"language\":\"GO\",\"protocoltype\":\"cloudevents\",\"env\":\"\",\"ip\":\"1.1.1.1\",\"protocolversion\":\"1.0\"}",
    "ttl": "10000",
    "uniqueId": "2",
    "seqNum": "2",
    "properties": {
        "contenttype": "application/json",
        "env": "",
        "idc": "idc-test",
        "ip": "1.1.1.1",
        "language": "GO",
        "passwd": "passwd",
        "pid": "1",
        "producergroup": "test-producer-group",
        "protocoldesc": "grpc",
        "protocoltype": "cloudevents",
        "protocolversion": "1.0",
        "seqnum": "2",
        "sys": "test",
        "uniqueid": "2",
        "username": "USERNAME"
    }
}
' localhost:10010 eventmesh.common.protocol.grpc.PublisherService/requestReply
```

### unsubscribe
```
grpcurl.exe -plaintext -d '
{
    "header": {
        "env": "11",
        "region": "sh",
        "idc": "test-idc",
        "ip": "169.254.45.15",
        "pid": "12345",
        "sys": "test",
        "username": "username",
        "password": "password",
        "language": "go",
        "protocolType": "cloudevents",
        "protocolVersion": "1.0",
        "protocolDesc": "nil"
    },
    "subscriptionItems": [
        {
            "topic":"grpc-topic",
            "mode":"CLUSTERING",
            "type":"SYNC"
        }
    ],
    "consumerGroup": "test-grpc-group",
    "url": "http://127.0.0.1:18080"
}
' localhost:10010 eventmesh.common.protocol.grpc.ConsumerService/unsubscribe
```

### subscribeStream
```
grpcurl.exe -plaintext -d '
{
}
' localhost:10010 eventmesh.common.protocol.grpc.ConsumerService/subscribeStream
```