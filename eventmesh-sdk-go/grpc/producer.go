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
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/log"

	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/grpc/proto"
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
	// client grpc producer client
	client proto.PublisherServiceClient
}

// newProducer create new producer instance to send events
func newProducer(grpcConn *grpc.ClientConn) (*eventMeshProducer, error) {
	return &eventMeshProducer{
		client: proto.NewPublisherServiceClient(grpcConn),
	}, nil
}

// Close recover all resource hold in the producer
func (e *eventMeshProducer) Close() error {
	log.Infof("close eventmesh producer")
	return nil
}

// Publish Async event publish
func (e *eventMeshProducer) Publish(ctx context.Context, msg *proto.SimpleMessage, opts ...grpc.CallOption) (*proto.Response, error) {
	log.Infof("publish event:%v", msg.String())
	resp, err := e.client.Publish(ctx, msg, opts...)
	if err != nil {
		log.Warnf("failed to publish msg, err:%v", err)
		return nil, err
	}

	log.Infof("success publish msg:%s", msg.String())
	return resp, nil
}

// RequestReply Sync event publish
func (e *eventMeshProducer) RequestReply(ctx context.Context, msg *proto.SimpleMessage, opts ...grpc.CallOption) (*proto.SimpleMessage, error) {
	log.Infof("request reply event:%v", msg.String())
	resp, err := e.client.RequestReply(ctx, msg, opts...)
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
	resp, err := e.client.BatchPublish(ctx, msg, opts...)
	if err != nil {
		log.Warnf("failed to batch publish msg, err:%v", err)
		return nil, err
	}

	log.Infof("success batch publish event")
	return resp, nil
}
