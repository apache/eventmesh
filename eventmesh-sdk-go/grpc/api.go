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
	"google.golang.org/grpc"
)

// OnMessage on receive message from eventmesh, used in subscribe message
type OnMessage func(*proto.SimpleMessage) interface{}

// Interface grpc client to producer and consumer message
type Interface interface {
	// Publish send message to eventmesh, without wait the response from other client
	Publish(ctx context.Context, msg *proto.SimpleMessage, opts ...grpc.CallOption) (*proto.Response, error)

	// RequestReply send message to eventmesh, and wait for the response
	RequestReply(ctx context.Context, msg *proto.SimpleMessage, opts ...grpc.CallOption) (*proto.SimpleMessage, error)

	// BatchPublish send batch message to eventmesh
	BatchPublish(ctx context.Context, msg *proto.BatchMessage, opts ...grpc.CallOption) (*proto.Response, error)

	// SubscribeWebhook consumer message in webhook, and OnMessage invoked when new message arrived
	SubscribeWebhook(item conf.SubscribeItem, callbackURL string) error

	// SubscribeStream stream subscribe the message
	SubscribeStream(item conf.SubscribeItem, handler OnMessage) error

	// UnSubscribe unsubcribe topic, and don't subscribe msg anymore
	UnSubscribe() error

	// Close release all resources in the client
	Close() error
}
