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
	// producer used to send msg to evenmesh
	*eventMeshProducer
	// consumer used to subscribe msg from eventmesh
	*eventMeshConsumer
	// consMap holds the connection remote, used free on clsoe
	consMap map[string]*grpc.ClientConn
	// cancel to close the client
	cancel context.CancelFunc
	// idg generate id api
	idg id.Interface
}

// NewEventMeshGRPCClient create new grpc client
func newEventMeshGRPCClient(cfg *conf.GRPCConfig, opts ...GRPCOption) (*eventMeshGRPCClient, error) {
	var (
		err         error
		consmap     = make(map[string]*grpc.ClientConn)
		ctx, cancel = context.WithCancel(context.Background())
	)
	if len(cfg.Hosts) == 0 {
		return nil, ErrNoMeshServer
	}
	defer func() {
		if err != nil {
			// if err != nil and the grpc.ClientConn is connected
			// we need to close it
			if len(consmap) != 0 {
				for host, v := range consmap {
					if errc := v.Close(); errc != nil {
						log.Warnf("failed to close conn with host:%, err:%v", host, errc)
					}
				}
			}
		}
	}()
	makeGRPCConn := func(host string) (*grpc.ClientConn, error) {
		addr := fmt.Sprintf("%v:%v", host, cfg.Port)
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
	// shuffle the hosts before make connection with eventmesh server
	rand.Seed(time.Now().UnixNano())
	rand.Shuffle(len(cfg.Hosts), func(i, j int) {
		cfg.Hosts[i], cfg.Hosts[j] = cfg.Hosts[j], cfg.Hosts[i]
	})
	for _, host := range cfg.Hosts {
		time.Sleep(time.Nanosecond * time.Duration(rand.Int31n(50)))
		conn, err := makeGRPCConn(host)
		if err != nil {
			continue
		}
		consmap[host] = conn
	}
	producer, err := newProducer(cfg, consmap)
	if err != nil {
		log.Warnf("failed to create producer, err:%v", err)
		return nil, err
	}
	cli.consMap = consmap
	cli.eventMeshProducer = producer
	cli.cancel = cancel
	if cfg.ConsumerConfig.Enabled {
		log.Infof("subscribe enabled")
		consumer, err := newConsumer(ctx, cfg, consmap)
		if err != nil {
			log.Warnf("failed to create producer, err:%v", err)
			return nil, err
		}
		if err := consumer.SubscribeStream(); err != nil {
			return nil, err
		}
		cli.eventMeshConsumer = consumer
	}

	return cli, nil
}

// Publish send message to eventmesh, without wait the response from other client
func (e *eventMeshGRPCClient) Publish(ctx context.Context, msg *proto.SimpleMessage, opts ...grpc.CallOption) (*proto.Response, error) {
	val := ctx.Value(GRPC_ID_KEY)
	if val == nil {
		ctx = context.WithValue(ctx, GRPC_ID_KEY, e.idg.Next())
	}
	return e.eventMeshProducer.Publish(ctx, msg, opts...)
}

// RequestReply send message to eventmesh, and wait for the response
func (e *eventMeshGRPCClient) RequestReply(ctx context.Context, msg *proto.SimpleMessage, opts ...grpc.CallOption) (*proto.SimpleMessage, error) {
	return e.eventMeshProducer.RequestReply(ctx, msg, opts...)
}

// BatchPublish send batch message to eventmesh
func (e *eventMeshGRPCClient) BatchPublish(ctx context.Context, msg *proto.BatchMessage, opts ...grpc.CallOption) (*proto.Response, error) {
	return e.eventMeshProducer.BatchPublish(ctx, msg, opts...)
}

// Subscribe consumer message, and OnMessage invoked when new message arrived
func (e *eventMeshGRPCClient) Subscribe(item conf.SubscribeItem, handler OnMessage) error {
	return e.eventMeshConsumer.Subscribe(item, handler)
}

// UnSubscribe unsubcribe topic, and don't subscribe msg anymore
func (e *eventMeshGRPCClient) UnSubscribe() error {
	return e.eventMeshConsumer.UnSubscribe()
}

// Close meshclient and free all resources
func (e *eventMeshGRPCClient) Close() error {
	log.Infof("close grpc client")
	e.cancel()
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
	for host, conn := range e.consMap {
		log.Infof("close conn with host:%s", host)
		if err := conn.Close(); err != nil {
			log.Infof("err in close conn with host:%s, err:%v", host, err)
		}
	}
	log.Infof("success close grpc client")
	return nil
}
