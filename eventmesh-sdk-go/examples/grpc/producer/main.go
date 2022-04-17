package main

import (
	"context"
	"fmt"
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/grpc"
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/grpc/conf"
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/grpc/proto"
)

func main() {
	cli, err := grpc.New(&conf.GRPCConfig{
		Hosts: []string{"101.43.84.47"},
		Port:  10205,
		ProducerConfig: conf.ProducerConfig{
			ProducerGroup:    "test-publish-group",
			LoadBalancerType: conf.Random,
		},
		ConsumerConfig: conf.ConsumerConfig{
			Enabled: false,
		},
	})
	if err != nil {
		panic(err)
	}
	defer func() {
		if err := cli.Close(); err != nil {
			panic(err)
		}
	}()

	for i := 0; i < 5; i++ {
		cli.RequestReply(context.TODO(), &proto.SimpleMessage{
			Header:        &proto.RequestHeader{},
			ProducerGroup: "grpc-producergroup",
			Topic:         "grpc-topic",
			Content:       fmt.Sprintf("request reply msg:%v", i),
			Ttl:           "60",
			UniqueId:      fmt.Sprintf("%v", i),
			SeqNum:        fmt.Sprintf("%v", i),
			Tag:           "grpc-tag",
			Properties: map[string]string{
				"from": "grpc",
				"for":  "test",
			},
		})
	}

}
