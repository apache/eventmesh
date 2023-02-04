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
	"sync"
	"time"

	cloudv2 "github.com/cloudevents/sdk-go/v2"
	"github.com/pkg/errors"

	"github.com/apache/incubator-eventmesh/eventmesh-server-go/config"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/log"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/pkg/util"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/plugin/connector"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/runtime/consts"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/runtime/core/wrapper"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/runtime/proto/pb"
)

var (
	ErrNoConnectorPlugin    = errors.New("no connector plugin found")
	ErrNewConsumerConnector = errors.New("create consumer connector err")
	ErrNewProducerConnector = errors.New("create producer connector err")
)

type EventMeshConsumer interface {
	Init() error
	Start() error
	ServiceState() consts.ServiceState
	RegisterClient(cli *GroupClient) bool
	DeRegisterClient(cli *GroupClient) bool
	Shutdown() error
}

type eventMeshConsumer struct {
	ConsumerGroup      string
	persistentConsumer wrapper.Consumer
	broadcastConsumer  wrapper.Consumer
	messageHandler     MessageHandler
	serviceState       consts.ServiceState
	// consumerGroupTopicConfig key is topic
	// value is ConsumerGroupTopicOption
	consumerGroupTopicConfig *sync.Map
}

func NewEventMeshConsumer(consumerGroup string) (EventMeshConsumer, error) {
	pushHandler, err := NewMessageHandler(consumerGroup)
	if err != nil {
		return nil, err
	}
	cons, err := wrapper.NewConsumer()
	if err != nil {
		return nil, ErrNewConsumerConnector
	}
	bcros, err := wrapper.NewConsumer()
	if err != nil {
		return nil, ErrNewConsumerConnector
	}
	return &eventMeshConsumer{
		ConsumerGroup:            consumerGroup,
		messageHandler:           pushHandler,
		persistentConsumer:       cons,
		broadcastConsumer:        bcros,
		consumerGroupTopicConfig: new(sync.Map),
	}, nil
}

func (e *eventMeshConsumer) ServiceState() consts.ServiceState {
	return e.serviceState
}

func (e *eventMeshConsumer) Init() error {
	// no topics, don't init the consumer
	if e.ConsumerGroupSize() == 0 {
		return nil
	}

	persistProps := make(map[string]string)
	persistProps["isBroadcast"] = "false"
	persistProps["consumerGroup"] = e.ConsumerGroup
	persistProps["eventMeshIDC"] = config.GlobalConfig().Common.IDC
	persistProps["instanceName"] = util.BuildMeshClientID(e.ConsumerGroup,
		config.GlobalConfig().Common.Cluster)
	if err := e.persistentConsumer.Init(persistProps); err != nil {
		return err
	}
	clusterEventListener := e.createEventListener(pb.Subscription_SubscriptionItem_CLUSTERING)
	e.persistentConsumer.RegisterListener(clusterEventListener)

	broadcastProps := make(map[string]string)
	broadcastProps["isBroadcast"] = "false"
	broadcastProps["consumerGroup"] = e.ConsumerGroup
	broadcastProps["eventMeshIDC"] = config.GlobalConfig().Common.IDC
	broadcastProps["instanceName"] = util.BuildMeshClientID(e.ConsumerGroup,
		config.GlobalConfig().Common.Cluster)
	if err := e.broadcastConsumer.Init(broadcastProps); err != nil {
		return err
	}
	broadcastEventListener := e.createEventListener(pb.Subscription_SubscriptionItem_BROADCASTING)
	e.broadcastConsumer.RegisterListener(broadcastEventListener)
	e.serviceState = consts.INITED

	log.Infof("init the eventmesh consumer success, group:%v", e.ConsumerGroup)
	return nil
}

func (e *eventMeshConsumer) Start() error {
	// no topics, don't start the consumer
	if e.ConsumerGroupSize() == 0 {
		return nil
	}

	e.consumerGroupTopicConfig.Range(func(key, value any) bool {
		topic := key.(string)
		opt := value.(ConsumerGroupTopicOption).SubscriptionMode()
		switch opt {
		case pb.Subscription_SubscriptionItem_CLUSTERING:
			e.persistentConsumer.Subscribe(topic)
		case pb.Subscription_SubscriptionItem_BROADCASTING:
			e.broadcastConsumer.Subscribe(topic)
		default:
			log.Warnf("un support sub mode:%v", opt)
		}
		return true
	})

	if err := e.broadcastConsumer.Start(); err != nil {
		return err
	}
	if err := e.persistentConsumer.Start(); err != nil {
		return err
	}

	e.serviceState = consts.RUNNING
	return nil
}

// RegisterClient Register client's topic information
// return true if this EventMeshConsumer required restart because of the topic changes
func (e *eventMeshConsumer) RegisterClient(cli *GroupClient) bool {
	var (
		consumerTopicOption ConsumerGroupTopicOption
		restart             = false
	)
	val, ok := e.consumerGroupTopicConfig.Load(cli.Topic)
	if !ok {
		consumerTopicOption = NewConsumerGroupTopicOption(cli.ConsumerGroup, cli.Topic, cli.SubscriptionMode, cli.GRPCType)
		e.consumerGroupTopicConfig.Store(cli.Topic, consumerTopicOption)
		restart = true
	} else {
		consumerTopicOption = val.(ConsumerGroupTopicOption)
	}
	consumerTopicOption.RegisterClient()(cli)
	return restart
}

// DeRegisterClient deregister client's topic information and return true if this EventMeshConsumer
// required restart because of the topic changes
// return true if the underlining EventMeshConsumer needs to restart later; false otherwise
func (e *eventMeshConsumer) DeRegisterClient(cli *GroupClient) bool {
	var (
		consumerTopicOption ConsumerGroupTopicOption
	)
	val, ok := e.consumerGroupTopicConfig.Load(cli.Topic)
	if !ok {
		return false
	}
	consumerTopicOption = val.(ConsumerGroupTopicOption)
	consumerTopicOption.DeregisterClient()(cli)
	e.consumerGroupTopicConfig.Delete(cli.Topic)
	return true
}

func (e *eventMeshConsumer) Shutdown() error {
	if err := e.persistentConsumer.Shutdown(); err != nil {
		return err
	}
	if err := e.broadcastConsumer.Shutdown(); err != nil {
		return err
	}
	e.serviceState = consts.STOPED
	return nil
}

func (e *eventMeshConsumer) ConsumerGroupSize() int {
	count := 0
	e.consumerGroupTopicConfig.Range(func(key, value any) bool {
		count++
		return true
	})
	return count
}

func (e *eventMeshConsumer) createEventListener(mode pb.Subscription_SubscriptionItem_SubscriptionMode) *connector.EventListener {
	return &connector.EventListener{
		Consume: func(event *cloudv2.Event, commitFunc connector.CommitFunc) error {
			var commitAction connector.EventMeshAction
			defer commitFunc(commitAction)

			eventclone := event.Clone()
			eventclone.SetExtension(consts.REQ_MQ2EVENTMESH_TIMESTAMP, time.Now().UnixMilli())
			topic := event.Subject()
			bizSeqNo := eventclone.Extensions()[consts.PROPERTY_MESSAGE_SEARCH_KEYS]
			uniqueID := eventclone.Extensions()[consts.RMB_UNIQ_ID]
			log.Infof("mq to eventmesh, topic:%v, bizSeqNo:%v, uniqueID:%v", topic, bizSeqNo, uniqueID)

			val, ok := e.consumerGroupTopicConfig.Load(topic)
			if !ok {
				log.Debugf("no active consumer for topic:%v", topic)
				commitAction = connector.CommitMessage
				return nil
			}

			topicConfig := val.(ConsumerGroupTopicOption)
			tpy := topicConfig.GRPCType()
			mctx := &MessageContext{
				GrpcType:         tpy,
				ConsumerGroup:    e.ConsumerGroup,
				SubscriptionMode: mode,
				Event:            &eventclone,
				TopicConfig:      topicConfig,
			}
			if err := e.messageHandler.Handler(mctx); err != nil {
				log.Warnf("handle msg err:%v, topic:%v, group:%v", err, topic, topicConfig.ConsumerGroup)
				// can not handle the message due to the capacity limit is reached
				// wait for 5 seconds and send this message back to mq and consume again
				time.Sleep(time.Second * 5)
				//e.sendMessageBack()
				commitAction = connector.CommitMessage
				return nil
			}

			commitAction = connector.ManualAck
			return nil
		},
	}
}
