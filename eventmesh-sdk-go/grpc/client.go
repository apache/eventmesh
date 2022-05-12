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
	"fmt"
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/common/id"
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/common/seq"
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/grpc/conf"
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/grpc/proto"
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/log"
	"google.golang.org/grpc"
	"google.golang.org/grpc/credentials/insecure"
	"math/rand"
	"time"
)

// New create new eventmesh grpc client
func New(cfg *conf.GRPCConfig, opts ...GRPCOption) (Interface, error) {
	cli, err := newEventMeshGRPCClient(cfg, opts...)
	if err != nil {
		return nil, err
	}

	return cli, err
}

// eventMeshGRPCClient define the grpc client for eventmesh api
type eventMeshGRPCClient struct {
	grpcConn *grpc.ClientConn
	// producer used to send msg to evenmesh
	*eventMeshProducer
	// consumer used to subscribe msg from eventmesh
	*eventMeshConsumer
	// cancel to close the client
	cancel context.CancelFunc
	// idg generate id api
	idg id.Interface
	// seqg generate uniq id
	seqg seq.Interface
}

// newEventMeshGRPCClient create new grpc client
func newEventMeshGRPCClient(cfg *conf.GRPCConfig, opts ...GRPCOption) (*eventMeshGRPCClient, error) {
	var (
		err         error
		ctx, cancel = context.WithCancel(context.Background())
		grpConn     *grpc.ClientConn
	)
	if err = conf.ValidateDefaultConf(cfg); err != nil {
		return nil, err
	}
	defer func() {
		if err != nil && grpConn != nil {
			// if err != nil and the grpc.ClientConn is connected
			// we need to close it
			if err := grpConn.Close(); err != nil {
				log.Warnf("failed to close conn with, err:%v", err)
			}
		}
	}()
	makeGRPCConn := func(host string, port int) (*grpc.ClientConn, error) {
		addr := fmt.Sprintf("%v:%v", host, port)
		conn, err := grpc.Dial(addr, grpc.WithTransportCredentials(insecure.NewCredentials()))
		if err != nil {
			log.Warnf("failed to make grpc conn with:%s, err:%v", addr, err)
			return nil, err
		}
		log.Infof("success make grpc conn with:%s", addr)
		return conn, nil
	}
	cli := &eventMeshGRPCClient{}
	for _, opt := range opts {
		opt(cli)
	}
	if cli.idg == nil {
		cli.idg = id.NewUUID()
	}
	if cli.seqg == nil {
		cli.seqg = seq.NewAtomicSeq()
	}
	time.Sleep(time.Nanosecond * time.Duration(rand.Int31n(50)))
	conn, err := makeGRPCConn(cfg.Host, cfg.Port)
	if err != nil {
		return nil, err
	}
	grpConn = conn
	producer, err := newProducer(grpConn)
	if err != nil {
		log.Warnf("failed to create producer, err:%v", err)
		return nil, err
	}
	cli.grpcConn = grpConn
	cli.eventMeshProducer = producer
	cli.cancel = cancel
	if cfg.ConsumerConfig.Enabled {
		log.Infof("subscribe enabled")
		consumer, err := newConsumer(ctx, cfg, grpConn, cli.idg, cli.seqg)
		if err != nil {
			log.Warnf("failed to create producer, err:%v", err)
			return nil, err
		}
		if err := consumer.startConsumerStream(); err != nil {
			return nil, err
		}
		cli.eventMeshConsumer = consumer
	}

	return cli, nil
}

// Publish send message to eventmesh, without wait the response from other client
func (e *eventMeshGRPCClient) Publish(ctx context.Context, msg *proto.SimpleMessage, opts ...grpc.CallOption) (*proto.Response, error) {
	ctx = e.setupContext(ctx)
	return e.eventMeshProducer.Publish(ctx, msg, opts...)
}

// RequestReply send message to eventmesh, and wait for the response
func (e *eventMeshGRPCClient) RequestReply(ctx context.Context, msg *proto.SimpleMessage, opts ...grpc.CallOption) (*proto.SimpleMessage, error) {
	ctx = e.setupContext(ctx)
	return e.eventMeshProducer.RequestReply(ctx, msg, opts...)
}

// BatchPublish send batch message to eventmesh
func (e *eventMeshGRPCClient) BatchPublish(ctx context.Context, msg *proto.BatchMessage, opts ...grpc.CallOption) (*proto.Response, error) {
	ctx = e.setupContext(ctx)
	return e.eventMeshProducer.BatchPublish(ctx, msg, opts...)
}

// SubscribeWebhook consumer message, and OnMessage invoked when new message arrived
func (e *eventMeshGRPCClient) SubscribeWebhook(item conf.SubscribeItem, callbackURL string) error {
	return e.Subscribe(item, callbackURL)
}

// SubscribeStream subscribe stream for topic
func (e *eventMeshGRPCClient) SubscribeStream(item conf.SubscribeItem, handler OnMessage) error {
	return e.SubscribeWithStream(item, handler)
}

// UnSubscribe unsubcribe topic, and don't subscribe msg anymore
func (e *eventMeshGRPCClient) UnSubscribe() error {
	return e.eventMeshConsumer.UnSubscribe()
}

// setupContext set up the context, add id if not exist
func (e *eventMeshGRPCClient) setupContext(ctx context.Context) context.Context {
	val := ctx.Value(GRPC_ID_KEY)
	if val == nil {
		ctx = context.WithValue(ctx, GRPC_ID_KEY, e.idg.Next())
	}
	return ctx
}

// Close meshclient and free all resources
func (e *eventMeshGRPCClient) Close() error {
	log.Infof("close grpc client")
	if e.cancel != nil {
		e.cancel()
	}
	if e.eventMeshProducer != nil {
		if err := e.eventMeshProducer.Close(); err != nil {
			log.Warnf("close producer err:%v", err)
		}
		e.eventMeshProducer = nil
	}
	if e.eventMeshConsumer != nil {
		if err := e.eventMeshConsumer.close(); err != nil {
			log.Warnf("close consumer err:%v", err)
		}
		e.eventMeshConsumer = nil
	}
	if err := e.grpcConn.Close(); err != nil {
		log.Warnf("err in close conn with err:%v", err)
	}

	log.Infof("success close grpc client")
	return nil
}
