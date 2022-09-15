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

package rocketmq

import (
	"context"
	"errors"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/plugin/connector"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/plugin/connector/rocketmq/client"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/plugin/connector/rocketmq/constants"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/plugin/connector/rocketmq/convert"
	"github.com/apache/rocketmq-client-go/v2/consumer"
	"github.com/apache/rocketmq-client-go/v2/primitive"
	ce "github.com/cloudevents/sdk-go/v2"
	"strconv"
	"sync"
)

type Consumer struct {
	rocketMQConsumer client.RocketMQConsumer
	started          bool
	mutex            sync.Mutex
	listener         *connector.EventListener
	subscribeHandler SubscribeHandler
}

// NewConsumer get new consumer, needs to be Initiated before using
func NewConsumer() *Consumer {
	return &Consumer{}
}

// InitConsumer init consumer by properties
func (c *Consumer) InitConsumer(properties map[string]string) error {
	consumer, err := client.NewRocketMQConsumerWrapper(properties)
	if err != nil {
		return err
	}
	c.rocketMQConsumer = consumer
	if c.rocketMQConsumer.IsBroadCasting() {
		c.subscribeHandler = &BroadCastingMessageSubscribeHandler{consumer: c}
	} else {
		c.subscribeHandler = &ClusteringMessageSubscribeHandler{consumer: c}
	}
	return nil
}

// IsStarted check if consumer is started
func (c *Consumer) IsStarted() bool {
	return c.rocketMQConsumer != nil && c.started
}

// IsClosed check if consumer is closed
func (c *Consumer) IsClosed() bool {
	return c.rocketMQConsumer != nil && !c.started
}

// Start make consumer started
func (c *Consumer) Start() error {
	if c.rocketMQConsumer == nil {
		return errors.New("start rocketmq consumer fail, producer should be initiated first")
	}
	c.mutex.Lock()
	defer c.mutex.Unlock()
	if !c.started {
		err := c.rocketMQConsumer.Start()
		if err != nil {
			return err
		}
		c.started = true
	}
	return nil
}

// Shutdown terminate the consumer
func (c *Consumer) Shutdown() error {
	if c.rocketMQConsumer == nil {
		return errors.New("shutdown rocketmq consumer fail, producer should be initiated first")
	}
	c.mutex.Lock()
	defer c.mutex.Unlock()
	if c.started {
		err := c.rocketMQConsumer.Shutdown()
		if err != nil {
			return err
		}
		c.started = false
	}
	return nil
}

// UpdateOffset always return error, since currently RocketMQ client doesn't support manual offset updating
func (c *Consumer) UpdateOffset(ctx context.Context, events []*ce.Event) error {
	// TODO support offset update
	return errors.New("fail to update offset, currently RocketMQ client doesn't support manual offset updating")
}

// Subscribe subscribe topic
func (c *Consumer) Subscribe(topicName string) error {
	return c.rocketMQConsumer.Subscribe(topicName, consumer.MessageSelector{}, c.subscribeHandler.handle)
}

// Unsubscribe unsubscribe topic
func (c *Consumer) Unsubscribe(topicName string) error {
	return c.rocketMQConsumer.Unsubscribe(topicName)
}

// RegisterEventListener listener's Consume function will be called when message is being consumed
func (c *Consumer) RegisterEventListener(listener *connector.EventListener) {
	c.listener = listener
}

// SubscribeHandler interface of message consume handler
type SubscribeHandler interface {
	handle(ctx context.Context, msg ...*primitive.MessageExt) (consumer.ConsumeResult, error)
	getMessageModel() consumer.MessageModel
}

// ClusteringMessageSubscribeHandler message consume handler of Clustering mode
type ClusteringMessageSubscribeHandler struct {
	consumer *Consumer
}

func (h *ClusteringMessageSubscribeHandler) getMessageModel() consumer.MessageModel {
	return consumer.Clustering
}

func (h *ClusteringMessageSubscribeHandler) handle(ctx context.Context, msg ...*primitive.MessageExt) (consumer.ConsumeResult, error) {
	if len(msg) == 0 {
		return consumer.ConsumeSuccess, nil
	}

	messageExt := msg[0]
	messageExt.WithProperty(constants.PropertyMessageBornTimestamp,
		strconv.FormatInt(messageExt.BornTimestamp, 10))
	messageExt.WithProperty(constants.PropertyMessageStoreTimestamp,
		strconv.FormatInt(messageExt.StoreTimestamp, 10))

	message := &messageExt.Message
	convert.TransferMessageSystemProperties(message)

	event, err := convert.NewRocketMQMessageReader(message).ToCloudEvent(context.Background())
	if err != nil {
		return consumer.ConsumeSuccess, nil
	}

	consumeResult := consumer.ConsumeSuccess
	commitFunction := func(action connector.EventMeshAction) error {
		switch action {
		case connector.CommitMessage:
			consumeResult = consumer.ConsumeSuccess
		case connector.ReconsumeLater:
			consumeResult = consumer.ConsumeRetryLater
		case connector.ManualAck:
			// currently, RocketMQ go client doesn't support manual offset updating, so just commit message here
			// TODO support manual offset updating
			consumeResult = consumer.ConsumeSuccess
		}
		return nil
	}
	h.consumer.listener.Consume(event, commitFunction)
	return consumeResult, nil
}

// BroadCastingMessageSubscribeHandler message consume handler of BroadCasting mode
type BroadCastingMessageSubscribeHandler struct {
	consumer *Consumer
}

func (h *BroadCastingMessageSubscribeHandler) getMessageModel() consumer.MessageModel {
	return consumer.BroadCasting
}

func (h *BroadCastingMessageSubscribeHandler) handle(ctx context.Context, msg ...*primitive.MessageExt) (consumer.ConsumeResult, error) {
	if len(msg) == 0 {
		return consumer.ConsumeSuccess, nil
	}

	messageExt := msg[0]
	messageExt.WithProperty(constants.PropertyMessageBornTimestamp,
		strconv.FormatInt(messageExt.BornTimestamp, 10))
	messageExt.WithProperty(constants.PropertyMessageStoreTimestamp,
		strconv.FormatInt(messageExt.StoreTimestamp, 10))

	message := &messageExt.Message
	convert.TransferMessageSystemProperties(message)

	event, err := convert.NewRocketMQMessageReader(message).ToCloudEvent(context.Background())
	if err != nil {
		return consumer.ConsumeSuccess, nil
	}

	consumeResult := consumer.ConsumeSuccess
	commitFunction := func(action connector.EventMeshAction) error {
		switch action {
		case connector.CommitMessage:
			consumeResult = consumer.ConsumeSuccess
		case connector.ReconsumeLater:
			consumeResult = consumer.ConsumeRetryLater
		case connector.ManualAck:
			// currently, RocketMQ go client doesn't support manual offset updating, so just commit message here
			// TODO support manual offset updating
			consumeResult = consumer.ConsumeSuccess
		}
		return nil
	}
	h.consumer.listener.Consume(event, commitFunction)
	return consumeResult, nil
}
