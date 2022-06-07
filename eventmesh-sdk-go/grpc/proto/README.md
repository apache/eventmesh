how to generate proto go files
---
generate go files by the protoc-gen-go owned by Google

1. install the latest protoc-gen-go and protoc-gen-go-grpc
```
go install google.golang.org/protobuf/cmd/protoc-gen-go
go install google.golang.org/grpc/cmd/protoc-gen-go-grpc
```
2. run command
```
protoc --go_out=. eventmesh-client.proto
protoc --go-grpc_out=. eventmesh-client.proto
```

if you use the latest version protoc-gen-go, and generate by the old command, you will got these error:
```
--go_out: protoc-gen-go: plugins are not supported; use 'protoc --go-grpc_out=...' to generate gRPC
```

