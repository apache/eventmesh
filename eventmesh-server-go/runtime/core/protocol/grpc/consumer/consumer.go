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
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/log"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/pkg/util"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/plugin"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/plugin/connector"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/runtime/consts"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/runtime/core/protocol/grpc/push"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/runtime/core/wrapper"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/runtime/proto/pb"
	cloudv2 "github.com/cloudevents/sdk-go/v2"
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
	// no topics, don't init the consumer
	if e.ConsumerGroupSize() == 0 {
		return nil
	}

	persistProps := make(map[string]string)
	persistProps["isBroadcast"] = "false"
	persistProps["consumerGroup"] = e.ConsumerGroup
	persistProps["eventMeshIDC"] = config.GlobalConfig().Server.GRPCOption.IDC
	persistProps["instanceName"] = util.BuildMeshClientID(e.ConsumerGroup,
		config.GlobalConfig().Server.GRPCOption.Cluster)
	if err := e.persistentConsumer.Init(persistProps); err != nil {
		return err
	}
	clusterEventListener := e.createEventListener(pb.Subscription_SubscriptionItem_CLUSTERING)
	e.persistentConsumer.RegisterListener(clusterEventListener)

	broadcastProps := make(map[string]string)
	broadcastProps["isBroadcast"] = "false"
	broadcastProps["consumerGroup"] = e.ConsumerGroup
	broadcastProps["eventMeshIDC"] = config.GlobalConfig().Server.GRPCOption.IDC
	broadcastProps["instanceName"] = util.BuildMeshClientID(e.ConsumerGroup,
		config.GlobalConfig().Server.GRPCOption.Cluster)
	if err := e.broadcastConsumer.Init(broadcastProps); err != nil {
		return err
	}
	broadcastEventListener := e.createEventListener(pb.Subscription_SubscriptionItem_BROADCASTING)
	e.broadcastConsumer.RegisterListener(broadcastEventListener)
	e.ServiceState = consts.INITED

	log.Infof("init the eventmesh consumer success, group:%v", e.ConsumerGroup)
	return nil
}

func (e *EventMeshConsumer) Start() error {
	return nil
}

// RegisterClient Register client's topic information
// return true if this EventMeshConsumer required restart because of the topic changes
func (e *EventMeshConsumer) RegisterClient(cli *GroupClient) bool {
	var (
		consumerTopicOption *ConsumerGroupTopicOption
		restart             = false
	)
	val, ok := e.consumerGroupTopicConfig.Load(cli.Topic)
	if !ok {
		consumerTopicOption = NewConsumerGroupTopicOption(cli.ConsumerGroup, cli.Topic, cli.SubscriptionMode, cli.GRPCType)
		e.consumerGroupTopicConfig.Store(cli.Topic, consumerTopicOption)
		restart = true
	} else {
		consumerTopicOption = val.(*ConsumerGroupTopicOption)
	}
	consumerTopicOption.RegisterClient(cli)
	return restart
}

// DeRegisterClient deregister client's topic information and return true if this EventMeshConsumer
// required restart because of the topic changes
// return true if the underlining EventMeshConsumer needs to restart later; false otherwise
func (e *EventMeshConsumer) DeRegisterClient(cli *GroupClient) bool {
	var (
		consumerTopicOption *ConsumerGroupTopicOption
	)
	val, ok := e.consumerGroupTopicConfig.Load(cli.Topic)
	if !ok {
		return false
	}
	consumerTopicOption = val.(*ConsumerGroupTopicOption)
	consumerTopicOption.DeregisterClient(cli)
	e.consumerGroupTopicConfig.Delete(cli.Topic)
	return true
}

func (e *EventMeshConsumer) Shutdown() error {
	return nil
}

func (e *EventMeshConsumer) ConsumerGroupSize() int {
	count := 0
	e.consumerGroupTopicConfig.Range(func(key, value any) bool {
		count++
		return true
	})
	return count
}

func (e *EventMeshConsumer) createEventListener(mode pb.Subscription_SubscriptionItem_SubscriptionMode) connector.EventListener {
	return connector.EventListener{
		Consume: func(event *cloudv2.Event, commitFunc connector.CommitFunc) error {
			return nil
		},
	}
}
