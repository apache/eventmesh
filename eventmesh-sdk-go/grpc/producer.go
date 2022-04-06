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

	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/grpc/conf"
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/grpc/loadbalancer"
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/grpc/proto"
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/internal/log"
	"google.golang.org/grpc"
)

var (
	ErrNoMeshServer = fmt.Errorf("no event mesh server provided")
	ErrLoadBalancer = fmt.Errorf("can not peek status server from loadbalancer")

	// LoadBalancerInput key set in the context, used to store the parameter
	// into context, such as clientIP in IPHash
	LoadBalancerInput = "LoadBalancerInput"
)

// eventMeshProducer producer for eventmesh
type eventMeshProducer struct {
	// loadbalancer loadbalancer for multiple grpc client
	loadbalancer loadbalancer.LoadBalancer
}

// newProducer create new producer instance to send events
func newProducer(cfg *conf.GRPCConfig, connsMap map[string]*grpc.ClientConn) (*eventMeshProducer, error) {
	producer := &eventMeshProducer{}
	var srvs []*loadbalancer.StatusServer
	for host, conn := range connsMap {
		cli := proto.NewPublisherServiceClient(conn)
		ss := loadbalancer.NewStatusServer(cli, host)
		srvs = append(srvs, ss)
	}

	producer.loadbalancer = loadbalancer.NewLoadBalancer(cfg.LoadBalancerType, srvs)
	return producer, nil
}

// Close recover all resource hold in the producer
func (e *eventMeshProducer) Close() error {
	log.Infof("close eventmesh producer")
	return nil
}

// Publish Async event publish
func (e *eventMeshProducer) Publish(ctx context.Context, msg *proto.SimpleMessage, opts ...grpc.CallOption) (*proto.Response, error) {
	log.Infof("publish event:%v", msg.String())
	cli, err := e.choose(ctx)
	if err != nil {
		return nil, err
	}

	resp, err := cli.Publish(ctx, msg, opts...)
	if err != nil {
		// TODO maybe check the err to do the breaks for client
		log.Warnf("failed to publish msg, err:%v", err)
		return nil, err
	}

	log.Infof("success publish event")
	return resp, nil
}

// RequestReply Sync event publish
func (e *eventMeshProducer) RequestReply(ctx context.Context, msg *proto.SimpleMessage, opts ...grpc.CallOption) (*proto.SimpleMessage, error) {
	log.Infof("request reply event:%v", msg.String())
	cli, err := e.choose(ctx)
	if err != nil {
		return nil, err
	}
	resp, err := cli.RequestReply(ctx, msg, opts...)
	if err != nil {
		log.Warnf("failed to request reply msg, err:%v", err)
		return nil, err
	}

	log.Infof("success request reply event")
	return resp, nil
}

// BatchPublish Async batch event publish
func (e *eventMeshProducer) BatchPublish(ctx context.Context, msg *proto.BatchMessage, opts ...grpc.CallOption) (*proto.Response, error) {
	log.Infof("request batch publish event:%v", msg.String())
	cli, err := e.choose(ctx)
	if err != nil {
		return nil, err
	}
	resp, err := cli.BatchPublish(ctx, msg, opts...)
	if err != nil {
		log.Warnf("failed to batch publish msg, err:%v", err)
		return nil, err
	}

	log.Infof("success batch publish event")
	return resp, nil
}

// choose choose a producer client from loadbalancer
func (e *eventMeshProducer) choose(ctx context.Context) (proto.PublisherServiceClient, error) {
	// try to load input from context
	val := ctx.Value(LoadBalancerInput)
	cli, err := e.loadbalancer.Choose(val)
	if err != nil {
		log.Warnf("failed to choose status server from loadbalancer, err:%v", err)
		return nil, ErrLoadBalancer
	}
	ss := cli.(loadbalancer.StatusServer)
	pubClient := ss.RealServer.(proto.PublisherServiceClient)
	return pubClient, nil
}
