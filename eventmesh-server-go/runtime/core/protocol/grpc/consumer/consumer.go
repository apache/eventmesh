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
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/config"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/plugin"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/runtime/consts"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/runtime/core/protocol/grpc/push"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/runtime/core/wrapper"
	"github.com/pkg/errors"
	"sync"
)

var (
	ErrNoConnectorPlugin    = errors.New("no connector plugin found")
	ErrNewConsumerConnector = errors.New("create consumer connector err")
	ErrNewProducerConnector = errors.New("create producer connector err")
)

type EventMeshConsumer struct {
	ConsumerGroup      string
	persistentConsumer *wrapper.Consumer
	broadcastConsumer  *wrapper.Consumer
	messageHandler     *push.MessageHandler
	ServiceState       consts.ServiceState
	// consumerGroupTopicConfig key is topic
	// value is ConsumerGroupTopicOption
	consumerGroupTopicConfig *sync.Map
}

func NewEventMeshConsumer(consumerGroup string) (*EventMeshConsumer, error) {
	pushHandler, err := push.NewMessageHandler(consumerGroup)
	if err != nil {
		return nil, err
	}
	connectorCfg, ok := config.GlobalConfig().Plugins[plugin.Connector]
	if !ok {
		return nil, ErrNoConnectorPlugin
	}
	name := connectorCfg[plugin.Name].Value
	cons, err := wrapper.NewConsumer(name)
	if err != nil {
		return nil, ErrNewConsumerConnector
	}
	bcros, err := wrapper.NewConsumer(name)
	if err != nil {
		return nil, ErrNewConsumerConnector
	}
	return &EventMeshConsumer{
		ConsumerGroup:            consumerGroup,
		messageHandler:           pushHandler,
		persistentConsumer:       cons,
		broadcastConsumer:        bcros,
		consumerGroupTopicConfig: new(sync.Map),
	}, nil
}

func (e *EventMeshConsumer) Init() error {
	return nil
}

func (e *EventMeshConsumer) Start() error {
	return nil
}

// RegisterClient Register client's topic information
// return nil if this EventMeshConsumer required restart because of the topic changes
func (e *EventMeshConsumer) RegisterClient(cli *GroupClient) error {
	val, ok := e.consumerGroupTopicConfig.Load(cli.Topic)
	if !ok {
		
	}
}

func (e *EventMeshConsumer) DeRegisterClient(cli *GroupClient) error {
	return nil
}

func (e *EventMeshConsumer) Shutdown() error {
	return nil
}
