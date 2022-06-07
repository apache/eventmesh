// Licensed to the Apache Software Foundation (ASF) under one or more
// contributor license agreements.  See the NOTICE file distributed with
// this work for additional information regarding copyright ownership.
// The ASF licenses this file to You under the Apache License, Version 2.0
// (the "License"); you may not use this file except in compliance with
// the License.  You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

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
		Host: "127.0.0.1",
		Port: 8086,
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
	assert.NoError(t, cli.SubscribeStream(conf.SubscribeItem{
		SubscribeType: 1,
		SubscribeMode: 1,
		Topic:         topic,
	}, func(message *proto.SimpleMessage) interface{} {
		t.Logf("receive sub msg:%v", message.String())
		return nil
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
			err := beat.sendMsg(beat.client)
			assert.NoError(t, err)
		})
	}
}
