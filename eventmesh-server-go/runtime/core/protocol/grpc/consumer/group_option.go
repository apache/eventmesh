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

package consumer

import (
	"container/list"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/log"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/runtime/proto/pb"
	"sync"
)

type RegisterClient func(*GroupClient)
type DeregisterClient func(*GroupClient)

// ConsumerGroupTopicOption refers to ConsumerGroupTopicConfig
type ConsumerGroupTopicOption struct {
	ConsumerGroup    string
	Topic            string
	SubscriptionMode pb.Subscription_SubscriptionItem_SubscriptionMode
	GRPCType         GRPCType
	RegisterClient   RegisterClient
	DeregisterClient DeregisterClient
}

func NewConsumerGroupTopicOption(cg string,
	topic string,
	mode pb.Subscription_SubscriptionItem_SubscriptionMode,
	gtype GRPCType) *ConsumerGroupTopicOption {
	return &ConsumerGroupTopicOption{
		ConsumerGroup:    cg,
		Topic:            topic,
		SubscriptionMode: mode,
		GRPCType:         gtype,
	}
}

type WebhookGroupTopicOption struct {
	*ConsumerGroupTopicOption

	// IDCWebhookURLs webhook urls seperated by IDC
	// key is IDC, value is *list.List
	IDCWebhookURLs *sync.Map

	// AllURLs all webhook urls, ignore idc
	AllURLs *list.List
}

func NewWebhookGroupTopicOption(cg string,
	topic string,
	mode pb.Subscription_SubscriptionItem_SubscriptionMode,
	gtype GRPCType) *WebhookGroupTopicOption {
	opt := &WebhookGroupTopicOption{
		ConsumerGroupTopicOption: NewConsumerGroupTopicOption(cg, topic, mode, WEBHOOK),
		IDCWebhookURLs:           new(sync.Map),
		AllURLs:                  list.New(),
	}
	opt.ConsumerGroupTopicOption.RegisterClient = func(cli *GroupClient) {
		if cli.GRPCType != WEBHOOK {
			log.Warnf("invalid grpc type:%v, with provide WEBHOOK", cli.GRPCType)
			return
		}
		iwu, ok := opt.IDCWebhookURLs.Load(cli.IDC)
		if !ok {
			newList := list.New()
			newList.PushBack(cli.URL)
			opt.IDCWebhookURLs.Store(cli.IDC, newList)
		} else {
			val := iwu.(list.List)
			val.PushBack(cli.URL)
			opt.IDCWebhookURLs.Store(cli.IDC, val)
		}
		opt.AllURLs.PushBack(cli.URL)
	}

	opt.ConsumerGroupTopicOption.DeregisterClient = func(cli *GroupClient) {
		val, ok := opt.IDCWebhookURLs.Load(cli.IDC)
		if !ok {
			return
		}
		// TODO think about goroutine safe
		idcURLs := val.(list.List)
		//idcURLs.
	}
	return opt
}
