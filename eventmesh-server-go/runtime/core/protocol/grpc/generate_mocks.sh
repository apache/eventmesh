#!/usr/bin/env bash

mockgen.exe -package=mocks -destination=./mocks/heartbeat_service.go github.com/apache/incubator-eventmesh/eventmesh-server-go/runtime/proto/pb HeartbeatServiceServer
mockgen.exe -package=mocks -destination=./mocks/consumer_service.go github.com/apache/incubator-eventmesh/eventmesh-server-go/runtime/proto/pb ConsumerServiceServer
mockgen.exe -package=mocks -destination=./mocks/producer_service.go github.com/apache/incubator-eventmesh/eventmesh-server-go/runtime/proto/pb PublisherServiceServer

mockgen.exe -package=mocks -destination=./mocks/consumer_manager.go github.com/apache/incubator-eventmesh/eventmesh-server-go/runtime/core/protocol/grpc ConsumerManager
mockgen.exe -package=mocks -destination=./mocks/consumer_mesh.go github.com/apache/incubator-eventmesh/eventmesh-server-go/runtime/core/protocol/grpc EventMeshConsumer
mockgen.exe -package=mocks -destination=./mocks/message_handler.go github.com/apache/incubator-eventmesh/eventmesh-server-go/runtime/core/protocol/grpc MessageHandler
mockgen.exe -package=mocks -destination=./mocks/processor.go github.com/apache/incubator-eventmesh/eventmesh-server-go/runtime/core/protocol/grpc Processor
mockgen.exe -package=mocks -destination=./mocks/producer_manager.go github.com/apache/incubator-eventmesh/eventmesh-server-go/runtime/core/protocol/grpc ProducerManager
mockgen.exe -package=mocks -destination=./mocks/producer_mesh.go github.com/apache/incubator-eventmesh/eventmesh-server-go/runtime/core/protocol/grpc EventMeshProducer
