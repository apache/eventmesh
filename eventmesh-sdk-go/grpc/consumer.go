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
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/log"
	"google.golang.org/grpc"
	"io"
)

// eventMeshConsumer consumer to implements the ConsumerService
type eventMeshConsumer struct {
	// consumerMap store all subscribe api client
	consumerMap map[string]proto.ConsumerServiceClient
	// topics subscribe topics
	topics map[string]*proto.Subscription_SubscriptionItem
	// cfg configuration
	cfg *conf.GRPCConfig
	// dispatcher for topic
	dispatcher *messageDispatcher
	// heartbeat used to keep the conn with eventmesh
	heartbeat *eventMeshHeartbeat
	// closeCtx close context
	closeCtx context.Context
}

// newConsumer create new consumer
func newConsumer(ctx context.Context, cfg *conf.GRPCConfig, connsMap map[string]*grpc.ClientConn) (*eventMeshConsumer, error) {
	mm := make(map[string]proto.ConsumerServiceClient)
	for host, cons := range connsMap {
		cli := proto.NewConsumerServiceClient(cons)
		mm[host] = cli
	}

	heartbeat, err := newHeartbeat(ctx, cfg, connsMap)
	if err != nil {
		log.Warnf("failed to create producer, err:%v", err)
		return nil, err
	}
	return &eventMeshConsumer{
		closeCtx:    ctx,
		consumerMap: mm,
		topics:      make(map[string]*proto.Subscription_SubscriptionItem),
		cfg:         cfg,
		heartbeat:   heartbeat,
		dispatcher:  newMessageDispatcher(cfg.ConsumerConfig.PoolSize),
	}, nil
}

// Subscribe subscribe topic for all eventmesh server
func (d *eventMeshConsumer) Subscribe(item conf.SubscribeItem, handler OnMessage) error {
	for host, cli := range d.consumerMap {
		log.Infof("subscribe topic:%v with server:%s", item, host)
		pitm := &proto.Subscription_SubscriptionItem{
			Topic: item.Topic,
			Mode:  proto.Subscription_SubscriptionItem_SubscriptionMode(item.SubscribeMode),
			Type:  proto.Subscription_SubscriptionItem_SubscriptionType(item.SubscribeType),
		}
		resp, err := cli.Subscribe(context.TODO(), &proto.Subscription{
			Header:            CreateHeader(d.cfg, eventmeshmessage),
			ConsumerGroup:     d.cfg.ConsumerGroup,
			SubscriptionItems: []*proto.Subscription_SubscriptionItem{pitm},
		})
		if err != nil {
			log.Warnf("failed to subscribe topic:%v, err :%v", pitm, err)
			return err
		}
		if err := d.dispatcher.addHandler(item.Topic, handler); err != nil {
			log.Warnf("failed to add handler for topic:%s", item.Topic)
			return err
		}
		d.topics[item.Topic] = pitm
		d.heartbeat.addHeartbeat(pitm)
		log.Infof("success subscribe with host:%s, resp:%s", host, resp.String())
	}
	return nil
}

// UnSubscribe unsubscribe topic with all eventmesh server
func (d *eventMeshConsumer) UnSubscribe() error {
	for host, cli := range d.consumerMap {
		log.Infof("unsubscribe with server:%s", host)
		resp, err := cli.Unsubscribe(context.TODO(), &proto.Subscription{
			Header:        CreateHeader(d.cfg, eventmeshmessage),
			ConsumerGroup: d.cfg.ConsumerGroup,
			SubscriptionItems: func() []*proto.Subscription_SubscriptionItem {
				var sitems []*proto.Subscription_SubscriptionItem
				for _, it := range d.topics {
					sitems = append(sitems, it)
				}
				return sitems
			}(),
		})
		if err != nil {
			log.Warnf("failed to subscribe topic:%v, err :%v", d.topics, err)
			return err
		}
		log.Infof("success unsubscribe with host:%s, resp:%s", host, resp.String())
	}
	return nil
}

// SubscribeStream subscribe stream, dispatch the message for all topic
func (d *eventMeshConsumer) SubscribeStream() error {
	for host, cli := range d.consumerMap {
		stream, err := cli.SubscribeStream(d.closeCtx)
		if err != nil {
			log.Warnf("failed to get subscribe stream for host:%s, err:%v", host, err)
			return err
		}
		go func() {
			ss := stream
			log.Infof("start rece stream, host:%s", host)
			for {
				msg, err := ss.Recv()
				if err == io.EOF {
					log.Infof("recv msg got io.EOF exit stream recv")
					break
				}
				if err != nil {
					log.Warnf("recv msg got err:%v, need to return", err)
					return
				}
				if err := d.dispatcher.onMessage(msg); err != nil {
					log.Warnf("dispatcher msg err:%v", err)
				}
			}
			log.Infof("close rece stream, host:%s", host)
		}()
	}
	return nil
}

func (d *eventMeshConsumer) close() error {
	if d.heartbeat != nil {
		if err := d.heartbeat.close(); err != nil {
			log.Warnf("failed to close heartbeat:%v", err)
		}
		d.heartbeat = nil
	}
	return nil
}
