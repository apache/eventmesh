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

type Producer struct {
	*Base
	ProducerConnector connector.Producer
}

// NewProducer create new producer to handle the grpc request
func NewProducer(connectorType string) (*Producer, error) {
	factory := plugin.Get(connector.ConsumerPluginType, connectorType).(connector.ProducerFactory)
	consu, err := factory.Get()
	if err != nil {
		return nil, err
	}
	return &Producer{
		Base:              DefaultBaseWrapper(),
		ProducerConnector: consu,
	}, nil
}

func (c *Producer) Send(ctx context.Context, event *eventv2.Event, callback connector.SendCallback) error {
	return c.ProducerConnector.Publish(ctx, event, callback)
}

// todo move the timeout to context is better
func (c *Producer) Request(ctx context.Context, event *eventv2.Event, callback connector.SendCallback) error {
	return c.ProducerConnector.Request(ctx, event, callback)
}

func (c *Producer) Reply(ctx context.Context, event *eventv2.Event, callback connector.SendCallback) error {
	return c.ProducerConnector.Reply(ctx, event, callback)
}

func (c *Producer) Start() error {
	if err := c.ProducerConnector.Start(); err != nil {
		return err
	}

	c.Base.Started.CAS(false, true)
	return nil
}

func (c *Producer) Shutdown() error {
	if err := c.ProducerConnector.Shutdown(); err != nil {
		return err
	}

	c.Base.Started.CAS(false, true)
	c.Base.Inited.CAS(false, true)
	return nil
}
