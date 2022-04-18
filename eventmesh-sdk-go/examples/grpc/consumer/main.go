package main

import (
	"fmt"
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/grpc"
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/grpc/conf"
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/grpc/proto"
	"time"
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
			Enabled:       true,
			ConsumerGroup: "test-consumer-group-subscribe",
			PoolSize:      5,
		},
		HeartbeatConfig: conf.HeartbeatConfig{
			Period:  time.Second * 5,
			Timeout: time.Second * 3,
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
	cli.Subscribe(conf.SubscribeItem{
		SubscribeMode: 1,
		SubscribeType: 1,
		Topic:         "grpc-topic",
	}, func(msg *proto.SimpleMessage) {
		fmt.Println("receive msg: " + msg.String())
	})
	time.Sleep(time.Hour)
}
