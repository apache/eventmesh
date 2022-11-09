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
    "url": "http://169.254.45.15:8080"
}
' localhost:10010 eventmesh.common.protocol.grpc.ConsumerService/subscribe
```
