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
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/internal/log"
	"google.golang.org/grpc"
)

// onMessage on receive message from eventmesh
type onMessage func(*proto.SimpleMessage)

// EventMeshConsumer consumer to implements the ConsumerService
type EventMeshConsumer struct {
	// consumerMap store all subscribe api client
	consumerMap map[string]proto.ConsumerServiceClient
	// cfg configuration
	cfg *conf.GRPCConfig
	// dispatcher for topic
	dispatcher *messageDispatcher
	// heartbeat used to keep the conn with eventmesh
	heartbeat *EventMeshHeartbeat
}

// NewConsumer create new consumer
func NewConsumer(ctx context.Context, cfg *conf.GRPCConfig, connsMap map[string]*grpc.ClientConn) (*EventMeshConsumer, error) {
	mm := make(map[string]proto.ConsumerServiceClient)
	for host, cons := range connsMap {
		cli := proto.NewConsumerServiceClient(cons)
		mm[host] = cli
	}

	heartbeat, err := NewHeartbeat(ctx, cfg, connsMap)
	if err != nil {
		log.Warnf("failed to create producer, err:%v", err)
		return nil, err
	}
	return &EventMeshConsumer{
		consumerMap: mm,
		cfg:         cfg,
		heartbeat:   heartbeat,
		dispatcher:  newMessageDispatcher(cfg.ConsumerConfig.PoolSize),
	}, nil
}

// Subscribe subscribe topic for all eventmesh server
func (d *EventMeshConsumer) Subscribe(item conf.SubscribeItem, handler onMessage) error {
	for host, cli := range d.consumerMap {
		log.Infof("subscribe topic:%v with server:%s", item, host)
		pitm := &proto.Subscription_SubscriptionItem{
			Topic: item.Topic,
			Mode:  proto.Subscription_SubscriptionItem_SubscriptionMode(item.SubscribeMode),
			Type:  proto.Subscription_SubscriptionItem_SubscriptionType(item.SubscribeType),
		}
		resp, err := cli.Subscribe(context.TODO(), &proto.Subscription{
			Header:        CreateHeader(d.cfg, eventmeshmessage),
			ConsumerGroup: d.cfg.ConsumerGroup,
			SubscriptionItems: func() []*proto.Subscription_SubscriptionItem {
				var sitems []*proto.Subscription_SubscriptionItem
				sitems = append(sitems, pitm)
				return sitems
			}(),
		})
		if err != nil {
			log.Warnf("failed to subscribe topic:%v, err :%v", d.cfg.Items, err)
			return err
		}
		if err := d.dispatcher.addHandler(item.Topic, handler); err != nil {
			log.Warnf("failed to add handler for topic:%s", item.Topic)
			return err
		}
		d.heartbeat.addHeartbeat(pitm)
		log.Infof("success subscribe with host:%s, resp:%s", host, resp.String())
	}
	return nil
}

// UnSubscribe unsubscribe topic with all eventmesh server
func (d *EventMeshConsumer) UnSubscribe() error {
	for host, cli := range d.consumerMap {
		log.Infof("unsubscribe topic:%v with server:%s", d.cfg.Items, host)
		resp, err := cli.Unsubscribe(context.TODO(), &proto.Subscription{
			Header:        CreateHeader(d.cfg, eventmeshmessage),
			ConsumerGroup: d.cfg.ConsumerGroup,
			SubscriptionItems: func() []*proto.Subscription_SubscriptionItem {
				var sitems []*proto.Subscription_SubscriptionItem
				for _, it := range d.cfg.Items {
					sitems = append(sitems, &proto.Subscription_SubscriptionItem{
						Topic: it.Topic,
						Mode:  proto.Subscription_SubscriptionItem_SubscriptionMode(it.SubscribeMode),
						Type:  proto.Subscription_SubscriptionItem_SubscriptionType(it.SubscribeType),
					})
				}
				return sitems
			}(),
		})
		if err != nil {
			log.Warnf("failed to subscribe topic:%v, err :%v", d.cfg.Items, err)
			return err
		}
		log.Infof("success subscribe with host:%s, resp:%s", host, resp.String())
	}
	return nil
}

// SubscribeStream subscribe stream, dispatch the message for all topic
func (d *EventMeshConsumer) SubscribeStream() error {
	return nil
}
