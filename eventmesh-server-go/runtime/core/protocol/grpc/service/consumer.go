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
	"github.com/panjf2000/ants/v2"

	"github.com/apache/incubator-eventmesh/eventmesh-server-go/runtime/proto/pb"
)

// Consumer grpc service
type Consumer struct {
	pb.UnimplementedConsumerServiceServer
	gctx          *GRPCContext
	subscribePool *ants.Pool
	replyPool     *ants.Pool
}

func NewConsumerServiceServer(gctx *GRPCContext) (*Consumer, error) {
	ss := config.GlobalConfig().Server.GRPCOption.SubscribePoolSize
	subPool, err := ants.NewPool(ss)
	if err != nil {
		return nil, err
	}
	rs := config.GlobalConfig().Server.GRPCOption.ReplyPoolSize
	replyPool, err := ants.NewPool(rs)
	if err != nil {
		return nil, err
	}
	return &Consumer{
		gctx:          gctx,
		subscribePool: subPool,
		replyPool:     replyPool,
	}, nil
}

func (c *Consumer) Subscribe(ctx context.Context, sub *pb.Subscription) (*pb.Response, error) {
	return nil, nil
}

func (c *Consumer) SubscribeStream(srv pb.ConsumerService_SubscribeStreamServer) error {
	return nil
}

func (c *Consumer) Unsubscribe(context.Context, *pb.Subscription) (*pb.Response, error) {
	return nil, nil
}
