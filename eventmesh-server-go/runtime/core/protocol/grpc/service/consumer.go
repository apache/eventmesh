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

package service

import (
	"context"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/config"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/log"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/runtime/core/protocol/grpc/processor"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/runtime/proto/pb"
	"github.com/panjf2000/ants"
	"google.golang.org/grpc"
)

// Consumer service for consumer, provide consumer grpc handler
type Consumer struct {
	subscribePool *ants.Pool
	replyPool     *ants.Pool
}

// PoolExceptionHandler handle the ants goroutine panic
func PoolExceptionHandler(in interface{}) {
	log.Errorf("err in groutine pool:%v", in)
}

// NewConsumer create new consumer to handle the grpc request
func NewConsumer() (*Consumer, error) {
	subPoolSize := config.GlobalConfig().Server.GRPCOption.SubscribePoolSize
	retryPoolSize := config.GlobalConfig().Server.GRPCOption.RetryPoolSize
	ps, err := ants.NewPool(subPoolSize, ants.WithPanicHandler(PoolExceptionHandler))
	if err != nil {
		return nil, err
	}
	pr, err := ants.NewPool(retryPoolSize, ants.WithPanicHandler(PoolExceptionHandler))
	if err != nil {
		return nil, err
	}
	return &Consumer{
		subscribePool: ps,
		replyPool:     pr,
	}, nil
}

func (c *Consumer) Subscribe(ctx context.Context, in *pb.Subscription, opts ...grpc.CallOption) (*pb.Response, error) {
	log.Info("subscribe with webhook, client:%v, topic:%v, webhook:%v", in.Header.Ip, in.SubscriptionItems, in.Url)
	c.subscribePool.Submit(func() {
		sw := processor.SubscribeWebHook{}

	})
	return nil, nil
}

func (c *Consumer) SubscribeStream(ctx context.Context, opts ...grpc.CallOption) (pb.ConsumerService_SubscribeStreamClient, error) {
	return nil, nil
}

func (c *Consumer) Unsubscribe(ctx context.Context, in *pb.Subscription, opts ...grpc.CallOption) (*pb.Response, error) {
	return nil, nil
}
