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
	"sync"
	"time"
)

var (
	// ErrHeartbeatResp err in sent heartbeat msg to mesh server, response not success
	ErrHeartbeatResp = fmt.Errorf("heartbeat response err")
)

// eventMeshHeartbeat heartbeat to keep the client conn
type eventMeshHeartbeat struct {
	// clientsMap hold all grp client to send heartbeat msg
	client proto.HeartbeatServiceClient
	// cfg the configuration for grpc client
	cfg *conf.GRPCConfig
	// closeCtx close context
	closeCtx context.Context
	// subscribeItems subscribe items, heartbeat for these topic
	subscribeItems []*proto.Subscription_SubscriptionItem
	// subscribeItemsLock lock for subscribeItems
	subscribeItemsLock *sync.RWMutex
}

// newHeartbeat create heartbeat service, and start a goroutine
// with all eventmesh server
func newHeartbeat(ctx context.Context, cfg *conf.GRPCConfig, grpcConn *grpc.ClientConn) (*eventMeshHeartbeat, error) {
	cli := proto.NewHeartbeatServiceClient(grpcConn)
	heartbeat := &eventMeshHeartbeat{
		client:             cli,
		cfg:                cfg,
		closeCtx:           ctx,
		subscribeItemsLock: new(sync.RWMutex),
	}
	go heartbeat.run()
	return heartbeat, nil
}

// run the ticker to send heartbeat msg
func (e *eventMeshHeartbeat) run() {
	log.Infof("start heartbeat goroutine")
	tick := time.NewTicker(e.cfg.Period)
	for {
		select {
		case <-tick.C:
			go e.sendMsg(e.client)
		case <-e.closeCtx.Done():
			log.Infof("exit heartbeat goroutine as closed")
			return
		}
	}
}

// sendMsg send heartbeat msg to eventmesh server
func (e *eventMeshHeartbeat) sendMsg(cli proto.HeartbeatServiceClient) error {
	log.Debugf("send heartbeat msg to server:%s")
	cancelCtx, cancel := context.WithTimeout(e.closeCtx, e.cfg.HeartbeatConfig.Timeout)
	defer cancel()
	e.subscribeItemsLock.RLock()
	defer e.subscribeItemsLock.RUnlock()
	msg := &proto.Heartbeat{
		Header:        CreateHeader(e.cfg),
		ClientType:    proto.Heartbeat_SUB,
		ConsumerGroup: e.cfg.ConsumerGroup,
		HeartbeatItems: func() []*proto.Heartbeat_HeartbeatItem {
			var items []*proto.Heartbeat_HeartbeatItem
			for _, tp := range e.subscribeItems {
				items = append(items, &proto.Heartbeat_HeartbeatItem{
					Topic: tp.Topic,
				})
			}
			return items
		}(),
	}
	resp, err := cli.Heartbeat(cancelCtx, msg)
	if err != nil {
		log.Warnf("failed to send heartbeat msg, err:%v", err)
		return err
	}
	if resp.RespCode != Success {
		log.Warnf("heartbeat msg return err, resp:%s", resp.String())
		return ErrHeartbeatResp
	}
	log.Debugf("success send heartbeat to server resp:%s", resp.String())
	return nil
}

// addHeartbeat add heartbeat for topic
func (e *eventMeshHeartbeat) addHeartbeat(item *proto.Subscription_SubscriptionItem) {
	e.subscribeItemsLock.Lock()
	defer e.subscribeItemsLock.Unlock()
	e.subscribeItems = append(e.subscribeItems, item)
}

// removeHeartbeat remove heartbeat for topic
func (e *eventMeshHeartbeat) removeHeartbeat(item *proto.Subscription_SubscriptionItem) {
	e.subscribeItemsLock.Lock()
	defer e.subscribeItemsLock.Unlock()
	var newSubscribeItems []*proto.Subscription_SubscriptionItem
	for _, it := range e.subscribeItems {
		if it.Topic == item.Topic {
			continue
		}
		newSubscribeItems = append(newSubscribeItems, it)
	}
	e.subscribeItems = newSubscribeItems
}

// close free the heartbeat resources
func (e *eventMeshHeartbeat) close() error {
	log.Infof("close heartbeat")
	return nil
}
