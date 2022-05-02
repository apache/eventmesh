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
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/grpc/proto"
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/log"
	"google.golang.org/grpc"
	"io"
)

var (
	// ErrSubscribeResponse subscribe response code not ok
	ErrSubscribeResponse = fmt.Errorf("subscribe response code err")
)

// eventMeshConsumer consumer to implements the ConsumerService
type eventMeshConsumer struct {
	// client subscribe api client
	client proto.ConsumerServiceClient
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
	// streamSubscribeChan chan to receive the subscribe request with stream type
	streamSubscribeChan chan *proto.Subscription
}

// newConsumer create new consumer
func newConsumer(ctx context.Context, cfg *conf.GRPCConfig, grpcConn *grpc.ClientConn) (*eventMeshConsumer, error) {
	cli := proto.NewConsumerServiceClient(grpcConn)
	heartbeat, err := newHeartbeat(ctx, cfg, grpcConn)
	if err != nil {
		log.Warnf("failed to create producer, err:%v", err)
		return nil, err
	}
	return &eventMeshConsumer{
		client:              cli,
		closeCtx:            ctx,
		topics:              make(map[string]*proto.Subscription_SubscriptionItem),
		cfg:                 cfg,
		heartbeat:           heartbeat,
		dispatcher:          newMessageDispatcher(cfg.ConsumerConfig.PoolSize),
		streamSubscribeChan: make(chan *proto.Subscription),
	}, nil
}

// startConsumerStream run stream goroutine to receive the msg send by stream not webhook
func (d *eventMeshConsumer) startConsumerStream() error {
	stream, err := d.client.SubscribeStream(d.closeCtx)
	if err != nil {
		log.Warnf("failed to get subscribe stream, err:%v", err)
		return err
	}
	go func() {
		ss := stream
		for {
			select {
			case <-d.closeCtx.Done():
				log.Infof("close consumer subscribe goroutine")
			case sub, ok := <-d.streamSubscribeChan:
				if ok {
					if err := ss.Send(sub); err != nil {
						log.Warnf("send subscribe stream msg err:%v", err)
					}
				}
			}
		}
	}()
	go func() {
		ss := stream
		log.Infof("start receive msg stream")
		for {
			msg, err := ss.Recv()
			if err == io.EOF {
				log.Infof("receive msg got io.EOF exit stream")
				break
			}
			if err != nil {
				log.Warnf("receive msg got err:%v, need to return", err)
				return
			}
			d.dispatcher.onMessage(msg)
		}
		log.Infof("close receive stream")
	}()

	return nil
}

// Subscribe topic for webhook
func (d *eventMeshConsumer) Subscribe(item conf.SubscribeItem, callbackURL string) error {
	log.Infof("subscribe with webhook topic:%v, url:%s", item, callbackURL)
	if callbackURL == "" {
		return fmt.Errorf("webhook subscribe err, url is empty")
	}
	subItem := &proto.Subscription_SubscriptionItem{
		Topic: item.Topic,
		Mode:  proto.Subscription_SubscriptionItem_SubscriptionMode(item.SubscribeMode),
		Type:  proto.Subscription_SubscriptionItem_SubscriptionType(item.SubscribeType),
	}
	subMsg := &proto.Subscription{
		Header:            CreateHeader(d.cfg),
		ConsumerGroup:     d.cfg.ConsumerGroup,
		SubscriptionItems: []*proto.Subscription_SubscriptionItem{subItem},
		Url:               callbackURL,
	}
	resp, err := d.client.Subscribe(context.TODO(), subMsg)
	if err != nil {
		log.Warnf("failed to subscribe topic:%v, err :%v", subItem, err)
		return err
	}
	if resp.RespCode != Success {
		log.Warnf("failed to subscribe resp:%v", resp.String())
		return ErrSubscribeResponse
	}
	d.topics[item.Topic] = subItem
	d.heartbeat.addHeartbeat(subItem)
	log.Infof("success subscribe with topic:%s, resp:%s", item.Topic, resp.String())
	return nil
}

// UnSubscribe unsubscribe topic with all eventmesh server
func (d *eventMeshConsumer) UnSubscribe() error {
	log.Infof("unsubscribe topics")
	resp, err := d.client.Unsubscribe(context.TODO(), &proto.Subscription{
		Header:        CreateHeader(d.cfg),
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
	log.Infof("success unsubscribe with resp:%s", resp.String())

	return nil
}

// SubscribeWithStream subscribe stream, dispatch the message for all topic
func (d *eventMeshConsumer) SubscribeWithStream(item conf.SubscribeItem, handler OnMessage) error {
	log.Infof("subscribe stream topic:%v", item)
	subItem := &proto.Subscription_SubscriptionItem{
		Topic: item.Topic,
		Mode:  proto.Subscription_SubscriptionItem_SubscriptionMode(item.SubscribeMode),
		Type:  proto.Subscription_SubscriptionItem_SubscriptionType(item.SubscribeType),
	}
	if err := d.addSubscribeHandler(item, handler); err != nil {
		return err
	}
	d.streamSubscribeChan <- &proto.Subscription{
		Header:            CreateHeader(d.cfg),
		ConsumerGroup:     d.cfg.ConsumerGroup,
		SubscriptionItems: []*proto.Subscription_SubscriptionItem{subItem},
	}

	log.Infof("success subscribe stream with topic:%s, resp:%s", item.Topic)
	return nil
}

func (d *eventMeshConsumer) addSubscribeHandler(item conf.SubscribeItem, handler OnMessage) error {
	subItem := &proto.Subscription_SubscriptionItem{
		Topic: item.Topic,
		Mode:  proto.Subscription_SubscriptionItem_SubscriptionMode(item.SubscribeMode),
		Type:  proto.Subscription_SubscriptionItem_SubscriptionType(item.SubscribeType),
	}
	if err := d.dispatcher.addHandler(item.Topic, handler); err != nil {
		log.Warnf("failed to add handler for topic:%s", item.Topic)
		return err
	}
	d.topics[item.Topic] = subItem
	d.heartbeat.addHeartbeat(subItem)
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
