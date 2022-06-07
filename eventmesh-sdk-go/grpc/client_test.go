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
	"google.golang.org/grpc"
	"testing"
	"time"
)

func Test_newEventMeshGRPCClient(t *testing.T) {
	type args struct {
		cfg *conf.GRPCConfig
	}
	tests := []struct {
		name    string
		args    args
		want    *eventMeshGRPCClient
		wantErr bool
	}{
		{
			name: "host is empty",
			args: args{cfg: &conf.GRPCConfig{
				Host: "",
			}},
			wantErr: true,
			want:    nil,
		},
		{
			name: "producer wrong",
			args: args{cfg: &conf.GRPCConfig{
				Host:           "1.1.1.1",
				ProducerConfig: conf.ProducerConfig{},
			}},
			wantErr: true,
			want:    nil,
		},
		{
			name: "client with send msg",
			args: args{cfg: &conf.GRPCConfig{
				Host:         "101.43.84.47",
				Port:         10205,
				ENV:          "sendmsgenv",
				Region:       "sh",
				IDC:          "idc01",
				SYS:          "test-system",
				ProtocolType: "grpc",
				ProducerConfig: conf.ProducerConfig{
					ProducerGroup: "test-producer-group",
				},
				Username: "user",
				Password: "passwd",
			}},
			want:    nil,
			wantErr: false,
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			cli, err := newEventMeshGRPCClient(tt.args.cfg)
			if (err != nil) != tt.wantErr {
				t.Errorf("newEventMeshGRPCClient() error = %v, wantErr %v", err, tt.wantErr)
				return
			}
			if err == nil {
				assert.NoError(t, cli.Close())
			}
		})
	}
}

func Test_multiple_set_context(t *testing.T) {
	root := context.Background()
	onec, cancel := context.WithTimeout(root, time.Second*5)
	defer cancel()
	valc := context.WithValue(onec, "test", "got")

	select {
	case <-valc.Done():
		val := valc.Value("test")
		t.Logf("5 s reached, value in context:%v", val)
	case <-time.After(time.Second * 10):
		t.Logf("ooor, 10s timeout")
	}

}

func Test_eventMeshGRPCClient_Publish(t *testing.T) {
	// run fake server
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	go runFakeServer(ctx)
	cli, err := New(&conf.GRPCConfig{
		Host: "127.0.0.1",
		Port: 8086,
		ProducerConfig: conf.ProducerConfig{
			ProducerGroup: "test-publish-group",
		},
		ConsumerConfig: conf.ConsumerConfig{
			Enabled: false,
		},
	})
	assert.NoError(t, err, "create grpc client")
	type args struct {
		ctx  context.Context
		msg  *proto.SimpleMessage
		opts []grpc.CallOption
	}
	tests := []struct {
		name    string
		args    args
		want    *proto.Response
		wantErr assert.ErrorAssertionFunc
	}{
		{
			name: "publish msg",
			args: args{
				ctx: context.TODO(),
				msg: &proto.SimpleMessage{
					Header: &proto.RequestHeader{},
					Topic:  "test-publish-topic",
				},
			},
			wantErr: func(t assert.TestingT, err error, i ...interface{}) bool {
				return true
			},
		},
		{
			name: "publish with timeout",
			args: args{
				ctx: func() context.Context {
					ctx, _ := context.WithTimeout(context.Background(), time.Second*10)
					return ctx
				}(),
				msg: &proto.SimpleMessage{
					Header: &proto.RequestHeader{},
					Topic:  "test-timeout-topic",
				},
			},
			wantErr: func(t assert.TestingT, err error, i ...interface{}) bool {
				return true
			},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got, err := cli.Publish(tt.args.ctx, tt.args.msg, tt.args.opts...)
			assert.NoError(t, err)
			t.Logf("receive publish response:%v", got.String())
			assert.NoError(t, cli.Close())
		})
	}
}

func Test_eventMeshGRPCClient_RequestReply(t *testing.T) {
	// run fake server
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	go runFakeServer(ctx)
	cli, err := New(&conf.GRPCConfig{
		Host: "127.0.0.1",
		Port: 8086,
		ProducerConfig: conf.ProducerConfig{
			ProducerGroup: "test-publish-group",
		},
		ConsumerConfig: conf.ConsumerConfig{
			Enabled: false,
		},
	})
	assert.NoError(t, err, "create grpc client")
	type args struct {
		ctx  context.Context
		msg  *proto.SimpleMessage
		opts []grpc.CallOption
	}
	tests := []struct {
		name    string
		args    args
		want    *proto.SimpleMessage
		wantErr assert.ErrorAssertionFunc
	}{
		{
			name: "test-request-reply",
			args: args{
				ctx: context.TODO(),
				msg: &proto.SimpleMessage{
					Header: &proto.RequestHeader{},
					Topic:  "test-request-reply-topic",
				},
			},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got, err := cli.RequestReply(tt.args.ctx, tt.args.msg, tt.args.opts...)
			assert.NoError(t, err)
			t.Logf("receive request reply response:%v", got.String())
			assert.NoError(t, cli.Close())
		})
	}
}

func Test_eventMeshGRPCClient_BatchPublish(t *testing.T) {
	// run fake server
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	go runFakeServer(ctx)
	cli, err := New(&conf.GRPCConfig{
		Host: "127.0.0.1",
		Port: 8086,
		ProducerConfig: conf.ProducerConfig{
			ProducerGroup: "test-publish-group",
		},
		ConsumerConfig: conf.ConsumerConfig{
			Enabled: false,
		},
	})
	assert.NoError(t, err, "create grpc client")
	type args struct {
		ctx  context.Context
		msg  *proto.BatchMessage
		opts []grpc.CallOption
	}
	tests := []struct {
		name    string
		args    args
		want    *proto.Response
		wantErr assert.ErrorAssertionFunc
	}{
		{
			name: "test batch publish",
			args: args{
				ctx: context.TODO(),
				msg: &proto.BatchMessage{
					Header:        &proto.RequestHeader{},
					ProducerGroup: "fake-batch-group",
					Topic:         "fake-batch-topic",
					MessageItem: []*proto.BatchMessage_MessageItem{
						{
							Content:  "batch-1",
							Ttl:      "1",
							UniqueId: "batch-id",
							SeqNum:   "1",
							Tag:      "tag",
							Properties: map[string]string{
								"from": "test",
								"type": "batch-msg",
							},
						},
						{
							Content:  "batch-2",
							Ttl:      "2",
							UniqueId: "batch-id",
							SeqNum:   "2",
							Tag:      "tag",
							Properties: map[string]string{
								"from": "test",
								"type": "batch-msg",
							},
						},
					},
				},
			},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got, err := cli.BatchPublish(tt.args.ctx, tt.args.msg, tt.args.opts...)
			assert.NoError(t, err)
			t.Logf("receive request reply response:%v", got.String())
			assert.NoError(t, cli.Close())
		})
	}
}

func Test_eventMeshGRPCClient_webhook_subscribe(t *testing.T) {
	ctx, cancel := context.WithTimeout(context.Background(), time.Second*3)
	defer cancel()
	go runWebhookServer(ctx)
	go runFakeServer(ctx)
	cli, err := New(&conf.GRPCConfig{
		Host: "127.0.0.1",
		Port: 8086,
		ProducerConfig: conf.ProducerConfig{
			ProducerGroup: "test-publish-group",
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
	assert.NoError(t, err, "create grpc client")
	assert.NoError(t, cli.SubscribeWebhook(conf.SubscribeItem{
		SubscribeMode: 1,
		SubscribeType: 1,
		Topic:         "topic-1",
	}, "http://localhost:8080/onmessage"))
	time.Sleep(time.Second * 5)
}

func Test_eventMeshGRPCClient_Subscribe(t *testing.T) {
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	go runFakeServer(ctx)
	cli, err := New(&conf.GRPCConfig{
		Host: "127.0.0.1",
		Port: 8086,
		ProducerConfig: conf.ProducerConfig{
			ProducerGroup: "test-publish-group",
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
	assert.NoError(t, err, "create grpc client")
	type args struct {
		item    conf.SubscribeItem
		handler OnMessage
	}
	tests := []struct {
		name    string
		args    args
		wantErr assert.ErrorAssertionFunc
	}{
		{
			name: "subcribe one",
			args: args{
				item: conf.SubscribeItem{
					SubscribeMode: 1,
					SubscribeType: 1,
					Topic:         "topic-1",
				},
				handler: func(message *proto.SimpleMessage) interface{} {
					t.Logf("receive subscribe response:%s", message.String())
					return nil
				},
			},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			err := cli.SubscribeStream(tt.args.item, tt.args.handler)
			assert.NoError(t, err)
			assert.NoError(t, cli.Close())
		})
	}
}

func Test_eventMeshGRPCClient_UnSubscribe(t *testing.T) {
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	go runFakeServer(ctx)
	cli, err := New(&conf.GRPCConfig{
		Host: "127.0.0.1",
		Port: 8086,
		ProducerConfig: conf.ProducerConfig{
			ProducerGroup: "test-publish-group",
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
	assert.NoError(t, err, "create grpc client")
	err = cli.SubscribeStream(conf.SubscribeItem{
		SubscribeMode: 1,
		SubscribeType: 1,
		Topic:         "topic-1",
	}, func(message *proto.SimpleMessage) interface{} {
		t.Logf("receive subscribe response:%s", message.String())
		return nil
	})
	assert.NoError(t, err, "subscribe err")
	tests := []struct {
		name    string
		wantErr assert.ErrorAssertionFunc
	}{
		{
			name: "unsubcribe",
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			assert.NoError(t, cli.UnSubscribe())
			assert.NoError(t, cli.Close())
		})
	}
}

type fakeidg struct {
}

func (f *fakeidg) Next() string {
	return "fake"
}

func Test_eventMeshGRPCClient_setupContext(t *testing.T) {
	type args struct {
		ctx context.Context
	}

	cli := &eventMeshGRPCClient{
		idg: &fakeidg{},
	}
	tests := []struct {
		name string
		args args
		want string
	}{
		{
			name: "setup with uid",
			args: args{
				ctx: context.WithValue(context.Background(), GRPC_ID_KEY, "value"),
			},
			want: "value",
		},
		{
			name: "setup without uid",
			args: args{
				ctx: context.TODO(),
			},
			want: "fake",
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			ctx := cli.setupContext(tt.args.ctx)
			assert.Equal(t, ctx.Value(GRPC_ID_KEY).(string), tt.want)
		})
	}
}
