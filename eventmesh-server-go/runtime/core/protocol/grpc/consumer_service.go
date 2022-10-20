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
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/config"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/log"
	"github.com/panjf2000/ants/v2"
	"io"
	"sync"

	"github.com/apache/incubator-eventmesh/eventmesh-server-go/runtime/proto/pb"
)

// ConsumerService grpc service
type ConsumerService struct {
	pb.UnimplementedConsumerServiceServer
	gctx          *GRPCContext
	subscribePool *ants.Pool
	replyPool     *ants.Pool
	msgToClient   chan *pb.SimpleMessage
	subFromClient chan *pb.Subscription
}

func NewConsumerServiceServer(gctx *GRPCContext) (*ConsumerService, error) {
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
	return &ConsumerService{
		gctx:          gctx,
		subscribePool: subPool,
		replyPool:     replyPool,
	}, nil
}

func (c *ConsumerService) Subscribe(ctx context.Context, sub *pb.Subscription) (*pb.Response, error) {
	log.Infof("cmd={}|{}|client2eventMesh|from={}", "subscribe", "grpc", sub.Header.Ip)
	c.subscribePool.Submit(func() {
		//processor.Subscribe()
	})
	return nil, nil
}

// SubscribeStream handle stream request
// with two groutine for Recv() and Send() message
// for Recv() goroutine if got err==io.EOF as the client close the stream(即客户端关闭stream)
// for Send() refers to https://github.com/grpc/grpc-go/issues/444
func (c *ConsumerService) SubscribeStream(stream pb.ConsumerService_SubscribeStreamServer) error {
	go func() {
		var (
			waitGroup sync.WaitGroup
		)
		waitGroup.Add(2)
		go func() {
			defer waitGroup.Done()
			for v := range c.msgToClient {
				if err := stream.Send(v); err != nil {
					log.Warnf("send msg err:%v", err)
				}
			}
		}()
		go func() {
			defer waitGroup.Done()
			for {
				req, err := stream.Recv()
				if err == io.EOF {
					log.Infof("receive io.EOF, exit the recv goroutine")
					break
				}
				if err != nil {
					log.Warnf("receive err:%v", err)
					break
				}
				if len(req.SubscriptionItems) == 0 {
					log.Infof("receive reply msg, protocol:GRPC, client:%v", req.Header.Ip)
					c.handleSubscribeReply(req, stream)
				} else {
					log.Infof("receive sub msg, protocol:GRPC, client:%v", req.Header.Ip)
					c.handleSubscriptionStream(req, stream)
				}
			}
		}()
		waitGroup.Wait()
	}()

	return nil
}

func (c *ConsumerService) Unsubscribe(context.Context, *pb.Subscription) (*pb.Response, error) {
	return nil, nil
}

func (c *ConsumerService) handleSubscriptionStream(sub *pb.Subscription, stream pb.ConsumerService_SubscribeStreamServer) error {
	c.subscribePool.Submit(func() {
		emiter := &EventEmitter{emitter: stream}
		SubscribeStreamProcessor(context.TODO(), c.gctx, emiter, sub)
	})
	return nil
}

func (c *ConsumerService) handleSubscribeReply(sub *pb.Subscription, stream pb.ConsumerService_SubscribeStreamServer) error {
	return nil
}
