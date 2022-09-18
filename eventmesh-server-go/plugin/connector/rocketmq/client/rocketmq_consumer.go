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

package client

import (
	"context"
	"errors"
	"fmt"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/plugin/connector/rocketmq/constants"
	"github.com/apache/rocketmq-client-go/v2"
	"github.com/apache/rocketmq-client-go/v2/consumer"
	"github.com/apache/rocketmq-client-go/v2/primitive"
	"strconv"
	"strings"
)

type SubscribeFunc func(context.Context, ...*primitive.MessageExt) (consumer.ConsumeResult, error)

// RocketMQConsumer RocketMQ consumer interface
type RocketMQConsumer interface {
	Start() error
	Shutdown() error
	Subscribe(topic string, selector consumer.MessageSelector, f SubscribeFunc) error
	Unsubscribe(topic string) error
	Suspend()
	Resume()
	IsBroadCasting() bool
}

// RocketMQConsumerWrapper consumer wrapper of RocketMQ client
type RocketMQConsumerWrapper struct {
	options      []consumer.Option
	consumer     rocketmq.PushConsumer
	messageModel consumer.MessageModel
}

// NewRocketMQConsumerWrapper get a consumer wrapper of RocketMQ client
func NewRocketMQConsumerWrapper(properties map[string]string) (RocketMQConsumer, error) {
	consumerWrapper := &RocketMQConsumerWrapper{messageModel: consumer.Clustering}

	options, err := consumerWrapper.getConsumerOptionsFromProperties(properties)
	if err != nil {
		return nil, err
	}

	rocketMQConsumer, err := rocketmq.NewPushConsumer(options...)
	if err != nil {
		return nil, err
	}
	consumerWrapper.consumer = rocketMQConsumer

	return consumerWrapper, nil
}

// Start wrapper start function
func (r *RocketMQConsumerWrapper) Start() error {
	return r.consumer.Start()
}

// Shutdown wrapper shutdown function
func (r *RocketMQConsumerWrapper) Shutdown() error {
	return r.consumer.Shutdown()
}

// Subscribe wrapper subscribe function
func (r *RocketMQConsumerWrapper) Subscribe(topic string, selector consumer.MessageSelector, f SubscribeFunc) error {
	return r.consumer.Subscribe(topic, selector, f)
}

// Unsubscribe wrapper unsubscribe function
func (r *RocketMQConsumerWrapper) Unsubscribe(topic string) error {
	return r.consumer.Unsubscribe(topic)
}

// Suspend wrapper suspend function
func (r *RocketMQConsumerWrapper) Suspend() {
	r.consumer.Suspend()
}

// Resume wrapper resume function
func (r *RocketMQConsumerWrapper) Resume() {
	r.consumer.Resume()
}

// IsBroadCasting check if consumer mode is broadcasting
func (r *RocketMQConsumerWrapper) IsBroadCasting() bool {
	return r.messageModel == consumer.BroadCasting
}

// getConsumerOptionsFromProperties convert properties map to client options
func (r *RocketMQConsumerWrapper) getConsumerOptionsFromProperties(properties map[string]string) ([]consumer.Option, error) {
	clientConfig, err := getClientConfigFromProperties(properties)
	if clientConfig == nil {
		return nil, err
	}
	options := make([]consumer.Option, 0)

	accessPoints := clientConfig.AccessPoints
	if len(accessPoints) == 0 {
		return nil, errors.New("fail to parse rocketmq consumer config, invalid access points")
	}

	// name server address
	options = append(options, consumer.WithNameServer(strings.Split(accessPoints, ",")))

	// max reconsume times
	if len(clientConfig.MaxReconsumeTimes) != 0 {
		maxReconsumeTimes, err := strconv.ParseInt(clientConfig.MaxReconsumeTimes, 10, 32)
		if err == nil {
			options = append(options, consumer.WithMaxReconsumeTimes(int32(maxReconsumeTimes)))
		}
	}

	// consume message model
	isBroadCasting := false
	if len(clientConfig.MessageModel) != 0 {
		switch clientConfig.MessageModel {
		case consumer.BroadCasting.String():
			options = append(options, consumer.WithConsumerModel(consumer.BroadCasting))
			isBroadCasting = true
			r.messageModel = consumer.BroadCasting
		default:
			options = append(options, consumer.WithConsumerModel(consumer.Clustering))
			r.messageModel = consumer.Clustering
		}
	}

	// consumer group
	if len(clientConfig.ConsumerGroup) == 0 {
		return nil, errors.New("fail to create rocketmq consumer, consumer group is empty")
	}
	consumerGroup := clientConfig.ConsumerGroup
	if isBroadCasting {
		consumerGroup = fmt.Sprintf("%s-%s", constants.ConsumerGroupBroadcastPrefix, consumerGroup)
	}
	options = append(options, consumer.WithGroupName(clientConfig.ConsumerGroup))

	// TODO consumeTimeout config, currently rocket mq go client doesn't support

	// instance name
	if len(clientConfig.InstanceName) != 0 {
		options = append(options, consumer.WithInstance(clientConfig.InstanceName))
	}

	return options, nil
}
