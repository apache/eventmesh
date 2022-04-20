package main

import (
	"context"
	"fmt"
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/grpc"
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/grpc/conf"
	"time"
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
			ProducerGroup:    "test-rr-group",
			LoadBalancerType: conf.Random,
		},
		ConsumerConfig: conf.ConsumerConfig{
			Enabled: false,
		},
	}
	cli, err := grpc.New(cfg)
	if err != nil {
		panic(err)
	}
	defer func() {
		if err := cli.Close(); err != nil {
			panic(err)
		}
	}()
	builder := grpc.NewMessageBuilder()
	builder.WithHeader(grpc.CreateHeader(cfg)).
		WithContent("test for rr go grpc").
		WithProperties(map[string]string{
			"from": "grpc",
			"for":  "test"}).
		WithProducerGroup("grpc-rr-producergroup").
		WithTag("grpc rr tag").
		WithTopic("grpc-rr-topic").
		WithTTL(time.Hour).
		WithSeqNO("1").
		WithUniqueID("1")

	msg, err := cli.RequestReply(context.TODO(), builder.SimpleMessage)
	if err != nil {
		fmt.Println("send rr msg err:" + err.Error())
		return
	}
	fmt.Println(msg.String())

}
