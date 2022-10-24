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
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/pkg/naming/registry"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/runtime/proto/pb"
	cloudv2 "github.com/cloudevents/sdk-go/v2"
	"golang.org/x/time/rate"
	"time"
)

// GRPCContext grpc server api, used to handle the client
type GRPCContext struct {
	ConsumerMgr *ConsumerManager
	ProducerMgr *ProducerManager
	RateLimiter *rate.Limiter
	Registry    registry.Registry
}

// New create new grpc server
func New() (*GRPCContext, error) {
	log.Infof("create new grpc serer")
	msgReqPerSeconds := config.GlobalConfig().Server.GRPCOption.MsgReqNumPerSecond
	limiter := rate.NewLimiter(rate.Limit(msgReqPerSeconds), 10)
	cmgr, err := NewConsumerManager()
	if err != nil {
		return nil, err
	}
	pmgr, err := NewProducerManager()
	if err != nil {
		return nil, err
	}

	registryName := config.GlobalConfig().Server.GRPCOption.RegistryName
	regis := registry.Get(registryName)
	return &GRPCContext{
		ConsumerMgr: cmgr,
		ProducerMgr: pmgr,
		RateLimiter: limiter,
		Registry:    regis,
	}, nil
}

func (g *GRPCContext) Start() error {
	if err := g.ProducerMgr.Start(); err != nil {
		return err
	}
	if err := g.ConsumerMgr.Start(); err != nil {
		return err
	}
	//if err := g.Registry.Start(); err != nil {
	//	return err
	//}
	return nil
}

//
//func (g *GRPCContext) SendResp(code *grpc.StatusCode) {
//	resp := &pb.Response{
//		RespCode: code.RetCode,
//		RespMsg:  code.ErrMsg,
//		RespTime: fmt.Sprintf("%v", time.Now().UnixMilli()),
//	}
//}

type MessageContext struct {
	MsgRandomNo      string
	SubscriptionMode pb.Subscription_SubscriptionItem_SubscriptionMode
	GrpcType         GRPCType
	ConsumerGroup    string
	Event            *cloudv2.Event
	TopicConfig      *ConsumerGroupTopicConfig
	// channel for server
	Consumer *EventMeshConsumer
}

// SendMessageContext context in produce message
type SendMessageContext struct {
	Ctx         context.Context
	Event       *cloudv2.Event
	BizSeqNO    string
	ProducerAPI *EventMeshProducer
	CreateTime  time.Time
}
