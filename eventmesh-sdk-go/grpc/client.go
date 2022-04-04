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
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/internal/log"
	"google.golang.org/grpc"
	"google.golang.org/grpc/credentials/insecure"
)

// EventMeshGRPCClient define the grpc client for eventmesh api
type EventMeshGRPCClient struct {
	// loadbalancer loadbalancer for multiple grpc client
	loadbalancer loadbalancer.LoadBalancer
	// producer used to send msg to evenmesh
	producer *EventMeshProducer
	// consumer used to subscribe msg from eventmesh
	consumer *EventMeshConsumer
	// closeCtx context to release all resources
	closeCtx context.Context
	// consMap holds the connection remote, used free on clsoe
	consMap map[string]*grpc.ClientConn
	// cancel to close the client
	cancel context.CancelFunc
}

// NewEventMeshGRPCClient create new grpc client
func NewEventMeshGRPCClient(cfg *conf.GRPCConfig) (*EventMeshGRPCClient, error) {
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
			// if err != nil and the grpc.ClientConn is connect
			// we need to close it
			if len(consmap) != 0 {
				for host, v := range consmap {
					if errc := v.Close(); errc != nil {
						log.Warnf("failed to close conn:%, err:%v", host, errc)
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
	for _, host := range cfg.Hosts {
		conn, err := makeGRPCConn(host)
		if err != nil {
			continue
		}
		consmap[host] = conn
	}
	producer, err := NewProducer(cfg, consmap)
	if err != nil {
		log.Warnf("failed to create producer, err:%v", err)
		return nil, err
	}
	cli := &EventMeshGRPCClient{
		consMap:  consmap,
		producer: producer,
		cancel:   cancel,
	}
	if len(cfg.ConsumerConfig.Items) > 0 {
		log.Infof("subscribe enable for topics:%v", cfg.Items)
		consumer, err := NewConsumer(ctx, cfg, consmap)
		if err != nil {
			log.Warnf("failed to create producer, err:%v", err)
			return nil, err
		}
		cli.consumer = consumer
	}

	return cli, nil
}
