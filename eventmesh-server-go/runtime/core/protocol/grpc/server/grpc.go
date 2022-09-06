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

package server

import (
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/config"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/log"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/pkg/naming/registry"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/runtime/core/protocol/grpc/consumer"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/runtime/core/protocol/grpc/producer"
	"golang.org/x/time/rate"
)

// GRPCServer grpc server api, used to handle the client
type GRPCServer struct {
	ConsumerMgr *consumer.Manager
	ProducerMgr *producer.Manager
	RateLimiter *rate.Limiter
	Registry    registry.Registry
}

// NewGRPCServer create new grpc server
func NewGRPCServer() (*GRPCServer, error) {
	log.Infof("create new grpc serer")
	msgReqPerSeconds := config.GlobalConfig().Server.GRPCOption.MsgReqNumPerSecond
	limiter := rate.NewLimiter(rate.Limit(msgReqPerSeconds), 10)
	cmgr, err := consumer.NewManager()
	if err != nil {
		return nil, err
	}
	pmgr, err := producer.NewManager()
	if err != nil {
		return nil, err
	}

	registryName := config.GlobalConfig().Server.GRPCOption.RegistryName
	regis := registry.Get(registryName)
	return &GRPCServer{
		ConsumerMgr: cmgr,
		ProducerMgr: pmgr,
		RateLimiter: limiter,
		Registry:    regis,
	}, nil
}

func (g *GRPCServer) Start() error {
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
