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

package standalone

import (
	"context"
	"sync"

	cloudevents "github.com/cloudevents/sdk-go/v2"
	"go.uber.org/atomic"

	"github.com/apache/incubator-eventmesh/eventmesh-server-go/pkg/connector"
)

// defaultPoolsize pool to process message
var defaultPoolsize = 10

// Consumer standalone comsumer message
type Consumer struct {
	broker    *Broker
	listener  connector.EventListener
	isStarted *atomic.Bool

	// subscrbes map to store the SubscribeProcessor
	// key is topicName ,value is SubscribeProcessor
	subscribes *sync.Map
}

// NewConsumer create new standalone consumer
func NewConsumer(ctx context.Context) (*Consumer, error) {
	return &Consumer{
		broker:     NewBroker(ctx),
		listener:   nil,
		isStarted:  atomic.NewBool(false),
		subscribes: new(sync.Map),
	}, nil
}

func (c *Consumer) Initialize(*connector.Properties) error {
	return nil
}

func (c *Consumer) UpdateOffset(events []*cloudevents.Event) {
	for _, event := range events {
		c.broker.UpdateOffset(event.Subject(), event.Extensions()[ExtensionOffset].(int64))
	}
}

func (c *Consumer) Subscribe(ctx context.Context, topicName string) {
	if _, has := c.subscribes.Load(topicName); has {
		return
	}

	c.broker.createTopicIfAbsent(topicName)
	p := NewSubscribeProcessor(ctx, topicName, c.broker, c.listener)
	c.subscribes.Store(topicName, p)
}

func (c *Consumer) UnSubscribe(topicName string) {
	p, has := c.subscribes.Load(topicName)
	if !has {
		return
	}
	p.(*SubscribeProcessor).Shutdown()
	c.subscribes.Delete(topicName)
}

func (c *Consumer) RegisterEventListener(lis connector.EventListener) {
	c.listener = lis
}

func (c *Consumer) IsStarted() bool {
	return c.isStarted.Load()
}

func (c *Consumer) IsClosed() bool {
	return !c.isStarted.Load()
}

func (c *Consumer) Start() {
	c.isStarted.CAS(false, true)
}

func (c *Consumer) Shutdown() {
	c.isStarted.CAS(true, false)
	c.subscribes.Range(func(k, v interface{}) bool {
		v.(*SubscribeProcessor).Shutdown()
		return true
	})
	c.subscribes = new(sync.Map)
}
