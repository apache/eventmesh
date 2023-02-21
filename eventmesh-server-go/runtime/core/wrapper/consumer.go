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

package wrapper

import (
	"context"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/plugin"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/plugin/connector"
	eventv2 "github.com/cloudevents/sdk-go/v2"
)

//go:generate mockgen -destination ./mocks/consumer.go -package mocks github.com/apache/incubator-eventmesh/eventmesh-server-go/runtime/core/wrapper Consumer
type Consumer interface {
	Subscribe(topicName string) error
	UnSubscribe(topicName string) error
	Init(props map[string]string) error
	Start() error
	Shutdown() error
	RegisterListener(lis *connector.EventListener)
	UpdateOffset(ctx context.Context, events []*eventv2.Event)
}

type consumer struct {
	*Base
	consumerConnector connector.Consumer
}

// NewConsumer create new consumer to handle the grpc request
func NewConsumer() (Consumer, error) {
	factory := plugin.GetByType(connector.PluginType).(connector.Factory)
	consu, err := factory.GetConsumer()
	if err != nil {
		return nil, err
	}
	return &consumer{
		Base:              DefaultBaseWrapper(),
		consumerConnector: consu,
	}, nil
}

func (c *consumer) Subscribe(topicName string) error {
	return c.consumerConnector.Subscribe(topicName)
}

func (c *consumer) UnSubscribe(topicName string) error {
	return c.consumerConnector.Unsubscribe(topicName)
}

func (c *consumer) Init(props map[string]string) error {
	if err := c.consumerConnector.InitConsumer(props); err != nil {
		return err
	}

	c.Base.Inited.CAS(false, true)
	return nil
}

func (c *consumer) Start() error {
	if err := c.consumerConnector.Start(); err != nil {
		return err
	}

	c.Base.Started.CAS(false, true)
	return nil
}

func (c *consumer) Shutdown() error {
	if err := c.consumerConnector.Shutdown(); err != nil {
		return err
	}

	c.Base.Started.CAS(false, true)
	c.Base.Inited.CAS(false, true)
	return nil
}

func (c *consumer) RegisterListener(lis *connector.EventListener) {
	c.consumerConnector.RegisterEventListener(lis)
}

func (c *consumer) UpdateOffset(ctx context.Context, events []*eventv2.Event) {
	c.consumerConnector.UpdateOffset(ctx, events)
}
