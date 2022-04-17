package grpc

import (
	"context"
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/grpc/conf"
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/grpc/proto"
	"github.com/stretchr/testify/assert"
	"testing"
	"time"
)

func Test_eventMeshHeartbeat_sendMsg(t *testing.T) {
	// run fake server
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	go runFakeServer(ctx)
	cli, err := New(&conf.GRPCConfig{
		Hosts: []string{"127.0.0.1"},
		Port:  8086,
		ProducerConfig: conf.ProducerConfig{
			ProducerGroup:    "test-publish-group",
			LoadBalancerType: conf.Random,
		},
		ConsumerConfig: conf.ConsumerConfig{
			Enabled:       true,
			ConsumerGroup: "fake-consumer",
			PoolSize:      5,
		},
		HeartbeatConfig: conf.HeartbeatConfig{
			Period:  time.Second * 5,
			Timeout: time.Second * 3,
		},
	})
	topic := "fake-topic"
	assert.NoError(t, cli.Subscribe(conf.SubscribeItem{
		SubscribeType: 1,
		SubscribeMode: 1,
		Topic:         topic,
	}, func(message *proto.SimpleMessage) {
		t.Logf("receive sub msg:%v", message.String())
	}))
	rcli := cli.(*eventMeshGRPCClient)
	beat := rcli.heartbeat
	assert.NoError(t, err, "create grpc client")
	defer assert.NoError(t, cli.Close())
	tests := []struct {
		name string
		want error
	}{
		{
			want: nil,
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			host := "127.0.0.1"
			beatcli := beat.clientsMap[host]
			err := beat.sendMsg(beatcli, host)
			assert.NoError(t, err)
		})
	}
}
