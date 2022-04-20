package main

import (
	"context"
	"fmt"
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/grpc"
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/grpc/conf"
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/grpc/proto"
)

func main() {
	cfg := &conf.GRPCConfig{
		Hosts:        []string{"101.43.84.47"},
		Port:         10205,
		ENV:          "go-grpc-test-env",
		Region:       "sh",
		IDC:          "pd",
		SYS:          "grpc-go",
		Username:     "grpc-go-username",
		Password:     "grpc-go-passwd",
		ProtocolType: grpc.EventmeshMessage,
		ProducerConfig: conf.ProducerConfig{
			ProducerGroup:    "test-batch-group",
			LoadBalancerType: conf.Random,
		},
		ConsumerConfig: conf.ConsumerConfig{
			Enabled: false,
		},
	}
	cli, err := grpc.New(cfg)
	if err != nil {
		fmt.Println("create publish client err:" + err.Error())
		return
	}
	defer func() {
		if err := cli.Close(); err != nil {
			panic(err)
		}
	}()
	batchMsg := &proto.BatchMessage{
		Header:        grpc.CreateHeader(cfg),
		ProducerGroup: "grpc-producergroup",
		Topic:         "grpc-batch-topic",
		MessageItem: []*proto.BatchMessage_MessageItem{
			{
				Content:  "test for batch publish go grpc -1",
				Ttl:      "1024",
				UniqueId: "110",
				SeqNum:   "111",
				Tag:      "batch publish tag 1",
				Properties: map[string]string{
					"from": "grpc",
					"type": "batch publish",
				},
			},
			{
				Content:  "test for batch publish go grpc",
				Ttl:      "1024",
				UniqueId: "210",
				SeqNum:   "211",
				Tag:      "batch publish tag 2",
				Properties: map[string]string{
					"from": "grpc",
					"type": "batch publish",
				},
			},
		},
	}
	resp, err := cli.BatchPublish(context.TODO(), batchMsg)
	if err != nil {
		panic(err)
	}
	fmt.Println(resp.String())
}
