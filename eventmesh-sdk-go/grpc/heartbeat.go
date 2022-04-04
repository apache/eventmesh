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
	"sync"
	"time"
)

// EventMeshHeartbeat heartbeat to keep the client conn
type EventMeshHeartbeat struct {
	// clientsMap hold all grp client to send heartbeat msg
	clientsMap map[string]proto.HeartbeatServiceClient
	// cfg the configuration for grpc client
	cfg *conf.GRPCConfig
	// closeCtx close context
	closeCtx context.Context
	// subscribeItems subscribe items, heartbeat for these topic
	subscribeItems []*proto.Subscription_SubscriptionItem
	// subscribeItemsLock lock for subscribeItems
	subscribeItemsLock *sync.RWMutex
}

// NewHeartbeat create heartbeat service, and start a goroutine
// with all eventmesh server
func NewHeartbeat(ctx context.Context, cfg *conf.GRPCConfig, consMap map[string]*grpc.ClientConn) (*EventMeshHeartbeat, error) {
	clientsMap := make(map[string]proto.HeartbeatServiceClient)
	for host, conn := range consMap {
		cli := proto.NewHeartbeatServiceClient(conn)
		clientsMap[host] = cli
	}

	heartbeat := &EventMeshHeartbeat{
		clientsMap:         clientsMap,
		cfg:                cfg,
		closeCtx:           ctx,
		subscribeItemsLock: new(sync.RWMutex),
	}
	go heartbeat.Run()
	return heartbeat, nil
}

// Run run the ticker to send heartbeat msg
func (e *EventMeshHeartbeat) Run() {
	log.Infof("start heartbeat goroutine")
	tick := time.NewTicker(e.cfg.Period)
	for {
		select {
		case <-tick.C:
			for host, cli := range e.clientsMap {
				go e.sendMsg(cli, host)
			}
		case <-e.closeCtx.Done():
			log.Infof("exit heartbeat goroutine as closed")
			return
		}
	}
}

// sendMsg send heartbeat msg to eventmesh server
func (e *EventMeshHeartbeat) sendMsg(cli proto.HeartbeatServiceClient, host string) error {
	log.Debugf("send heartbeat msg to server:%s", host)
	cancelCtx, cancel := context.WithTimeout(e.closeCtx, e.cfg.Timeout)
	defer cancel()
	e.subscribeItemsLock.RLock()
	defer e.subscribeItemsLock.RUnlock()
	msg := &proto.Heartbeat{
		Header:        CreateHeader(e.cfg, eventmeshmessage),
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
		log.Warnf("failed to send heartbeat msg to :%s, err:%v", host, err)
		return err
	}
	log.Debugf("success send heartbeat to server:%s, resp:%s", host, resp.String())
	return nil
}

// addHeartbeat add heartbeat for topic
func (e *EventMeshHeartbeat) addHeartbeat(item *proto.Subscription_SubscriptionItem) {
	e.subscribeItemsLock.Lock()
	defer e.subscribeItemsLock.Unlock()
	e.subscribeItems = append(e.subscribeItems, item)
}

// removeHeartbeat remove heartbeat for topic
func (e *EventMeshHeartbeat) removeHeartbeat(item *proto.Subscription_SubscriptionItem) {
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
