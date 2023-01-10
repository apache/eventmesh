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

package consumer

import (
	"context"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/config"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/log"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/runtime/core/protocol/grpc/emitter"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/runtime/core/protocol/grpc/producer"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/runtime/proto/pb"
	"github.com/panjf2000/ants/v2"
	"io"
	"time"
)

var defaultAsyncTimeout = time.Second * 5

// ConsumerService grpc service
type ConsumerService struct {
	pb.UnimplementedConsumerServiceServer
	consumerManager ConsumerManager
	producerManager producer.ProducerManager
	process         Processor
	subscribePool   *ants.Pool
	replyPool       *ants.Pool
	msgToClient     chan *pb.SimpleMessage
	subFromClient   chan *pb.Subscription
}

func NewConsumerServiceServer(consumerManager ConsumerManager, producerManager producer.ProducerManager) (*ConsumerService, error) {
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
		consumerManager: consumerManager,
		producerManager: producerManager,
		subscribePool:   subPool,
		replyPool:       replyPool,
		process:         &processor{},
	}, nil
}

func (c *ConsumerService) Subscribe(ctx context.Context, sub *pb.Subscription) (*pb.Response, error) {
	log.Infof("cmd=%v|%v|client2eventMesh|from=%v", "subscribe", "grpc", sub.Header.Ip)
	tmCtx, cancel := context.WithTimeout(ctx, defaultAsyncTimeout)
	defer cancel()
	var (
		resp    *pb.Response
		errChan = make(chan error)
		err     error
	)
	c.subscribePool.Submit(func() {
		resp, err = c.process.Subscribe(c.consumerManager, sub)
		errChan <- err
	})
	select {
	case <-tmCtx.Done():
		log.Warnf("timeout in subscribe")
	case <-errChan:
		break
	}
	if err != nil {
		log.Warnf("failed to subscribe webhook, err:%v", err)
	}
	return resp, err
}

// SubscribeStream handle stream request
// with two groutine for Recv() and Send() message
// for Recv() goroutine if got err==io.EOF as the client close the stream(即客户端关闭stream)
// for Send() refers to https://github.com/grpc/grpc-go/issues/444
func (c *ConsumerService) SubscribeStream(stream pb.ConsumerService_SubscribeStreamServer) error {
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
	return nil
}

func (c *ConsumerService) Unsubscribe(ctx context.Context, sub *pb.Subscription) (*pb.Response, error) {
	log.Infof("cmd=%v|%v|client2eventMesh|from=%v", "unsubscribe", "grpc", sub.Header.Ip)
	tmCtx, cancel := context.WithTimeout(ctx, defaultAsyncTimeout)
	defer cancel()
	var (
		resp    *pb.Response
		errChan = make(chan error)
		err     error
	)
	c.subscribePool.Submit(func() {
		resp, err = c.process.UnSubscribe(c.consumerManager, sub)
		errChan <- err
	})
	select {
	case <-tmCtx.Done():
		log.Warnf("timeout in subscribe")
	case <-errChan:
		break
	}
	if err != nil {
		log.Warnf("failed to subscribe webhook, err:%v", err)
	}
	return resp, err
}

func (c *ConsumerService) handleSubscriptionStream(sub *pb.Subscription, stream pb.ConsumerService_SubscribeStreamServer) error {
	c.subscribePool.Submit(func() {
		emiter := emitter.NewEventEmitter(stream)
		c.process.SubscribeStream(c.consumerManager, emiter, sub)
	})
	return nil
}

func (c *ConsumerService) handleSubscribeReply(sub *pb.Subscription, stream pb.ConsumerService_SubscribeStreamServer) error {
	c.replyPool.Submit(func() {
		emiter := emitter.NewEventEmitter(stream)
		reply := sub.Reply
		c.process.ReplyMessage(context.TODO(), c.producerManager, emiter, &pb.SimpleMessage{
			Header:        sub.Header,
			ProducerGroup: reply.ProducerGroup,
			Content:       reply.Content,
			UniqueId:      reply.UniqueId,
			SeqNum:        reply.SeqNum,
			Topic:         reply.Topic,
			Ttl:           reply.Ttl,
			Properties:    reply.Properties,
		})
	})
	return nil
}
