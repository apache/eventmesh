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
	"github.com/apache/eventmesh/eventmesh-sdk-go/grpc/conf"
	"github.com/apache/eventmesh/eventmesh-sdk-go/grpc/proto"
	. "github.com/onsi/ginkgo"
	. "github.com/onsi/gomega"
	"github.com/stretchr/testify/assert"
	"google.golang.org/grpc"
	"time"
)

type fakeidg struct {
}

func (f *fakeidg) Next() string {
	return "fake"
}

var _ = Describe("client test", func() {

	Context("newEventMeshGRPCClient() test ", func() {
		type args struct {
			cfg *conf.GRPCConfig
		}

		It("host is empty", func() {
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
			}

			for _, tt := range tests {
				cli, err := newEventMeshGRPCClient(tt.args.cfg)
				if tt.wantErr {
					Ω(err).To(HaveOccurred())
				} else {
					Ω(err).NotTo(HaveOccurred())
				}

				if cli != nil {
					Ω(cli.Close()).NotTo(HaveOccurred())
				}
			}
		})

		It("producer wrong", func() {
			tests := []struct {
				name    string
				args    args
				want    *eventMeshGRPCClient
				wantErr bool
			}{
				{
					name: "producer wrong",
					args: args{cfg: &conf.GRPCConfig{
						Host:           "1.1.1.1",
						ProducerConfig: conf.ProducerConfig{},
					}},
					wantErr: false,
					want:    nil,
				},
			}

			for _, tt := range tests {
				cli, err := newEventMeshGRPCClient(tt.args.cfg)
				if tt.wantErr {
					Ω(err).To(HaveOccurred())
				} else {
					Ω(err).NotTo(HaveOccurred())

				}

				if cli != nil {
					Ω(cli.Close()).NotTo(HaveOccurred())
				}
			}
		})

		It("client with send msg", func() {
			tests := []struct {
				name    string
				args    args
				want    *eventMeshGRPCClient
				wantErr bool
			}{
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
				cli, err := newEventMeshGRPCClient(tt.args.cfg)
				if tt.wantErr {
					Ω(err).To(HaveOccurred())
				} else {
					Ω(err).NotTo(HaveOccurred())

				}

				if cli != nil {
					Ω(cli.Close()).NotTo(HaveOccurred())
				}
			}
		})

	})

	Context("multiple_set_context() test ", func() {
		It("should done", func() {
			root := context.Background()
			onec, cancel := context.WithTimeout(root, time.Second*3)
			defer cancel()
			val := "test"
			go func() {

				select {
				case <-onec.Done():
					val = "test"
				case <-time.After(time.Second * 1):
					val = ""
				}
			}()

			time.Sleep(2 * time.Second)
			Ω(val).To(Equal(""))
		})

		It("should timeout", func() {
			root := context.Background()
			onec, cancel := context.WithTimeout(root, time.Second*1)
			defer cancel()

			val := "test"
			go func() {

				select {
				case <-onec.Done():
					val = "test"
					break
				case <-time.After(time.Second * 3):
					val = ""
				}
			}()

			time.Sleep(2 * time.Second)
			Ω(val).To(Equal("test"))
		})

	})

	Context("eventMeshGRPCClient_Publish test ", func() {

		type args struct {
			ctx  context.Context
			msg  *proto.SimpleMessage
			opts []grpc.CallOption
		}

		It("publish msg", func() {
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
			Ω(err).NotTo(HaveOccurred())

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
			}

			for _, tt := range tests {
				_, err := cli.Publish(tt.args.ctx, tt.args.msg, tt.args.opts...)
				Ω(err).NotTo(HaveOccurred())
			}

			Ω(cli.Close()).NotTo(HaveOccurred())

		})

		It("publish with timeout", func() {
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
			Ω(err).NotTo(HaveOccurred())
			tests := []struct {
				name    string
				args    args
				want    *proto.Response
				wantErr assert.ErrorAssertionFunc
			}{
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
				_, err := cli.Publish(tt.args.ctx, tt.args.msg, tt.args.opts...)
				Ω(err).NotTo(HaveOccurred())
			}

			Ω(cli.Close()).NotTo(HaveOccurred())

		})

	})

	Context("eventMeshGRPCClient_RequestReply test ", func() {
		type args struct {
			ctx  context.Context
			msg  *proto.SimpleMessage
			opts []grpc.CallOption
		}

		It("test-request-reply", func() {
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
			Ω(err).NotTo(HaveOccurred())

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
				_, err := cli.RequestReply(tt.args.ctx, tt.args.msg, tt.args.opts...)
				Ω(err).NotTo(HaveOccurred())
			}
			Ω(cli.Close()).NotTo(HaveOccurred())
		})
	})

	Context("eventMeshGRPCClient_RequestReply test ", func() {
		type args struct {
			ctx  context.Context
			msg  *proto.BatchMessage
			opts []grpc.CallOption
		}

		It("test batch publish", func() {
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
			Ω(err).NotTo(HaveOccurred())

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
				_, err := cli.BatchPublish(tt.args.ctx, tt.args.msg, tt.args.opts...)
				Ω(err).NotTo(HaveOccurred())

			}
			Ω(cli.Close()).NotTo(HaveOccurred())
		})
	})

	Context("eventMeshGRPCClient_webhook_subscribe test ", func() {
		It("webhook_subscribe success", func() {
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
			Ω(err).NotTo(HaveOccurred())
			Ω(cli.SubscribeWebhook(conf.SubscribeItem{
				SubscribeMode: 1,
				SubscribeType: 1,
				Topic:         "topic-1",
			}, "http://localhost:8080/onmessage")).NotTo(HaveOccurred())
			time.Sleep(time.Second * 5)
			Ω(cli.Close()).NotTo(HaveOccurred())
		})
	})

	Context("eventMeshGRPCClient_Subscribe test ", func() {

		type args struct {
			item    conf.SubscribeItem
			handler OnMessage
		}

		It("subcribe one success", func() {
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
			Ω(err).NotTo(HaveOccurred())

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
							return nil
						},
					},
				},
			}
			for _, tt := range tests {
				err := cli.SubscribeStream(tt.args.item, tt.args.handler)
				Ω(err).NotTo(HaveOccurred())
			}
			Ω(cli.Close()).NotTo(HaveOccurred())
		})
	})

	Context("eventMeshGRPCClient_UnSubscribe test ", func() {
		It("unsubcribe success", func() {
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
			Ω(err).NotTo(HaveOccurred())

			err = cli.SubscribeStream(conf.SubscribeItem{
				SubscribeMode: 1,
				SubscribeType: 1,
				Topic:         "topic-1",
			}, func(message *proto.SimpleMessage) interface{} {
				return nil
			})
			Ω(err).NotTo(HaveOccurred())
			Ω(cli.UnSubscribe()).NotTo(HaveOccurred())
			Ω(cli.Close()).NotTo(HaveOccurred())
		})
	})

	Context("eventMeshGRPCClient_UnSubscribe test ", func() {
		type args struct {
			ctx context.Context
		}
		It("unsubcribe success", func() {
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
				ctx := cli.setupContext(tt.args.ctx)
				Ω(ctx.Value(GRPC_ID_KEY).(string)).To(Equal(tt.want))
			}

			Ω(cli.Close()).NotTo(HaveOccurred())
		})
	})
})
