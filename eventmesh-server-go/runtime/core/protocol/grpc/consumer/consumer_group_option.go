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
	"fmt"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/log"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/runtime/consts"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/runtime/core/protocol/grpc/emitter"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/runtime/proto/pb"
	"github.com/liyue201/gostl/ds/set"
	"sync"
)

type RegisterClient func(*GroupClient)
type DeregisterClient func(*GroupClient)

//go:generate mockgen -destination ./mocks/consumer_group_option.go -package mocks github.com/apache/incubator-eventmesh/eventmesh-server-go/runtime/core/protocol/grpc/consumer ConsumerGroupTopicOption

type ConsumerGroupTopicOption interface {
	ConsumerGroup() string
	Topic() string
	SubscriptionMode() pb.Subscription_SubscriptionItem_SubscriptionMode
	GRPCType() consts.GRPCType
	RegisterClient() RegisterClient
	DeregisterClient() DeregisterClient
	IDCURLs() *sync.Map
	AllURLs() *set.Set
	AllEmiters() *set.Set
	IDCEmiters() *sync.Map
	Size() int
}

// BaseConsumerGroupTopicOption refers to ConsumerGroupTopicConfig
type BaseConsumerGroupTopicOption struct {
	consumerGroup    string
	topic            string
	subscriptionMode pb.Subscription_SubscriptionItem_SubscriptionMode
	gRPCType         consts.GRPCType
	registerClient   RegisterClient
	deregisterClient DeregisterClient
}

func NewConsumerGroupTopicOption(
	cg string, topic string,
	mode pb.Subscription_SubscriptionItem_SubscriptionMode,
	gtype consts.GRPCType) ConsumerGroupTopicOption {
	if gtype == consts.WEBHOOK {
		return NewWebhookGroupTopicOption(cg, topic, mode, gtype)
	}
	return NewWStreamGroupTopicOption(cg, topic, mode, gtype)
}

// WebhookGroupTopicOption topic option for subscribe with webhook
type WebhookGroupTopicOption struct {
	*BaseConsumerGroupTopicOption

	// IDCWebhookURLs webhook urls seperated by IDC
	// key is IDC, value is vector.Vector
	idcWebhookURLs *sync.Map

	// AllURLs all webhook urls, ignore idc
	allURLs *set.Set
}

func (b *BaseConsumerGroupTopicOption) ConsumerGroup() string {
	return b.consumerGroup
}
func (b *BaseConsumerGroupTopicOption) Topic() string {
	return b.topic
}
func (b *BaseConsumerGroupTopicOption) SubscriptionMode() pb.Subscription_SubscriptionItem_SubscriptionMode {
	return b.subscriptionMode
}
func (b *BaseConsumerGroupTopicOption) GRPCType() consts.GRPCType {
	return b.gRPCType
}
func (b *BaseConsumerGroupTopicOption) RegisterClient() RegisterClient {
	return b.registerClient
}
func (b *BaseConsumerGroupTopicOption) DeregisterClient() DeregisterClient {
	return b.deregisterClient
}

func (b *WebhookGroupTopicOption) IDCURLs() *sync.Map {
	return b.idcWebhookURLs
}

func (b *WebhookGroupTopicOption) AllURLs() *set.Set {
	return b.allURLs
}

func (b *WebhookGroupTopicOption) AllEmiters() *set.Set {
	panic("webhook no emiter")
}

func (b *WebhookGroupTopicOption) IDCEmiters() *sync.Map {
	panic("webhook no emiter")
}

func (b *WebhookGroupTopicOption) Size() int {
	return b.allURLs.Size()
}

func NewWebhookGroupTopicOption(cg string,
	topic string,
	mode pb.Subscription_SubscriptionItem_SubscriptionMode,
	gtype consts.GRPCType) ConsumerGroupTopicOption {
	opt := &WebhookGroupTopicOption{
		BaseConsumerGroupTopicOption: &BaseConsumerGroupTopicOption{
			consumerGroup:    cg,
			topic:            topic,
			subscriptionMode: mode,
			gRPCType:         gtype,
		},
		idcWebhookURLs: new(sync.Map),
		allURLs:        set.New(set.WithGoroutineSafe()),
	}
	opt.BaseConsumerGroupTopicOption.registerClient = func(cli *GroupClient) {
		if cli.GRPCType != consts.WEBHOOK {
			log.Warnf("invalid grpc type:%v, with provide WEBHOOK", cli.GRPCType)
			return
		}
		iwu, ok := opt.idcWebhookURLs.Load(cli.IDC)
		if !ok {
			newIDCURLs := set.New(set.WithGoroutineSafe())
			newIDCURLs.Insert(cli.URL)
			opt.idcWebhookURLs.Store(cli.IDC, newIDCURLs)
		} else {
			val := iwu.(*set.Set)
			val.Insert(cli.URL)
			opt.idcWebhookURLs.Store(cli.IDC, val)
		}
		opt.allURLs.Insert(cli.URL)
	}

	opt.BaseConsumerGroupTopicOption.deregisterClient = func(cli *GroupClient) {
		val, ok := opt.idcWebhookURLs.Load(cli.IDC)
		if !ok {
			return
		}
		idcURLs := val.(*set.Set)
		idcURLs.Erase(cli.URL)
		opt.allURLs.Erase(cli.URL)
	}
	return opt
}

// StreamGroupTopicOption topic option for subscribe with stream
type StreamGroupTopicOption struct {
	*BaseConsumerGroupTopicOption
	// Key: IDC Value: list of emitters with Client_IP:port
	idcEmitterMap *sync.Map
	// Key: IDC Value: list of emitters
	idcEmitters *sync.Map
	// all emitters
	totalEmitters *set.Set
}

func (b *StreamGroupTopicOption) RegisterClient() RegisterClient {
	return b.registerClient
}
func (b *StreamGroupTopicOption) DeregisterClient() DeregisterClient {
	return b.deregisterClient
}

func (b *StreamGroupTopicOption) IDCURLs() *sync.Map {
	panic("stream no idc urls")
}

func (b *StreamGroupTopicOption) AllURLs() *set.Set {
	panic("stream no all urls")
}

func (b *StreamGroupTopicOption) AllEmiters() *set.Set {
	return b.totalEmitters
}

func (b *StreamGroupTopicOption) IDCEmiters() *sync.Map {
	return b.idcEmitters
}
func (b *StreamGroupTopicOption) Size() int {
	return b.totalEmitters.Size()
}

func (b *StreamGroupTopicOption) buildIdcEmitter() {
	newIDCEmiters := new(sync.Map)
	b.idcEmitterMap.Range(func(key, value interface{}) bool {
		e1 := value.(*sync.Map)
		elist := set.New(set.WithGoroutineSafe())
		e1.Range(func(k1, v1 interface{}) bool {
			elist.Insert(v1.(emitter.EventEmitter))
			return true
		})
		newIDCEmiters.Store(key, elist)
		return true
	})
	b.idcEmitters = newIDCEmiters
}

func (b *StreamGroupTopicOption) buildTotalEmitter() {
	s2 := set.New(set.WithGoroutineSafe())
	b.idcEmitters.Range(func(key, value interface{}) bool {
		s3 := value.(*set.Set)
		for iter := s3.Begin(); iter.IsValid(); iter.Next() {
			s2.Insert(iter)
		}
		return true
	})
	b.totalEmitters = s2
}

func uniqClient(ip, pid string) string {
	return fmt.Sprintf("%v:%v", ip, pid)
}

func NewWStreamGroupTopicOption(cg string,
	topic string,
	mode pb.Subscription_SubscriptionItem_SubscriptionMode,
	gtype consts.GRPCType) ConsumerGroupTopicOption {
	opt := &StreamGroupTopicOption{
		BaseConsumerGroupTopicOption: &BaseConsumerGroupTopicOption{
			consumerGroup:    cg,
			topic:            topic,
			subscriptionMode: mode,
			gRPCType:         gtype,
		},
		idcEmitterMap: new(sync.Map),
		idcEmitters:   new(sync.Map),
		totalEmitters: set.New(set.WithGoroutineSafe()),
	}
	opt.registerClient = func(cli *GroupClient) {
		idc := cli.IDC
		clientIP := cli.IP
		pid := cli.PID
		emt := cli.Emiter
		key := uniqClient(clientIP, pid)
		var emiter *sync.Map
		em, ok := opt.idcEmitterMap.Load(idc)
		if !ok {
			emiter = new(sync.Map)
			opt.idcEmitterMap.Store(idc, emiter)
		} else {
			emiter = em.(*sync.Map)
		}
		emiter.Store(key, emt)
		opt.buildIdcEmitter()
		opt.buildTotalEmitter()
	}
	opt.deregisterClient = func(cli *GroupClient) {
		idc := cli.IDC
		clientIP := cli.IP
		pid := cli.PID
		num := 0
		key := uniqClient(clientIP, pid)
		em, ok := opt.idcEmitterMap.Load(idc)
		if !ok {
			return
		}
		emiter := em.(*sync.Map)
		emiter.Delete(key)
		emiter.Range(func(key, value any) bool {
			num++
			return true
		})
		if num == 0 {
			opt.idcEmitterMap.Delete(key)
		}
		opt.buildIdcEmitter()
		opt.buildTotalEmitter()
	}
	return opt
}
