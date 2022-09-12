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
	"encoding/json"
	"errors"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/plugin/connector/rocketmq/config"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/plugin/connector/rocketmq/utils"
	"github.com/apache/rocketmq-client-go/v2"
	"github.com/apache/rocketmq-client-go/v2/primitive"
	"github.com/apache/rocketmq-client-go/v2/producer"
	"strconv"
	"strings"
	"time"
)

// RocketMQProducer RocketMQ producer interface
type RocketMQProducer interface {
	Start() error
	Shutdown() error
	SendSync(ctx context.Context, msg ...*primitive.Message) (*primitive.SendResult, error)
	SendAsync(ctx context.Context, mq func(ctx context.Context, result *primitive.SendResult, err error),
		msg ...*primitive.Message) error
	SendOneWay(ctx context.Context, msg ...*primitive.Message) error
	Request(ctx context.Context, ttl time.Duration, msg *primitive.Message) (*primitive.Message, error)
	RequestAsync(ctx context.Context, ttl time.Duration, callback func(ctx context.Context,
		msg *primitive.Message, err error), msg *primitive.Message) error
}

// RocketMQProducerWrapper producer wrapper of RocketMQ client
type RocketMQProducerWrapper struct {
	options  []producer.Option
	producer rocketmq.Producer
}

// NewRocketMQProducerWrapper get a producer wrapper of RocketMQ client
func NewRocketMQProducerWrapper(properties map[string]string) (*RocketMQProducerWrapper, error) {
	options, err := getOptionsFromProperties(properties)
	if err != nil {
		return nil, err
	}

	rocketmqProducer, err := rocketmq.NewProducer(options...)
	if err != nil {
		return nil, err
	}

	return &RocketMQProducerWrapper{
		options:  options,
		producer: rocketmqProducer,
	}, nil
}

// Start wrapper start function
func (r *RocketMQProducerWrapper) Start() error {
	return r.producer.Start()
}

// Shutdown wrapper shutdown function
func (r *RocketMQProducerWrapper) Shutdown() error {
	return r.producer.Shutdown()
}

// SendSync wrapper send sync function
func (r *RocketMQProducerWrapper) SendSync(ctx context.Context, msg ...*primitive.Message) (*primitive.SendResult, error) {
	return r.producer.SendSync(ctx, msg...)
}

// SendAsync wrapper send async function
func (r *RocketMQProducerWrapper) SendAsync(ctx context.Context, mq func(ctx context.Context,
	result *primitive.SendResult, err error), msg ...*primitive.Message) error {
	return r.producer.SendAsync(ctx, mq, msg...)
}

// SendOneWay wrapper send one way function
func (r *RocketMQProducerWrapper) SendOneWay(ctx context.Context, msg ...*primitive.Message) error {
	return r.producer.SendOneWay(ctx, msg...)
}

// Request wrapper request function
func (r *RocketMQProducerWrapper) Request(ctx context.Context, ttl time.Duration,
	msg *primitive.Message) (*primitive.Message, error) {
	return r.producer.Request(ctx, ttl, msg)
}

// RequestAsync wrapper request async function
func (r *RocketMQProducerWrapper) RequestAsync(ctx context.Context, ttl time.Duration,
	callback func(ctx context.Context, msg *primitive.Message, err error), msg *primitive.Message) error {
	return r.producer.RequestAsync(ctx, ttl, callback, msg)
}

// getOptionsFromProperties convert properties map to client options
func getOptionsFromProperties(properties map[string]string) ([]producer.Option, error) {
	clientConfig, err := getClientConfigFromProperties(properties)
	if clientConfig == nil {
		return nil, err
	}
	options := make([]producer.Option, 0)

	accessPoints := clientConfig.AccessPoints
	if len(accessPoints) == 0 {
		return nil, errors.New("fail to parse rocketmq producer config, invalid access points")
	}

	// name server address
	options = append(options, producer.WithNameServer(strings.Split(accessPoints, ",")))

	// instance name
	producerId := utils.GetInstanceName()
	options = append(options, producer.WithInstanceName(producerId))
	clientConfig.InstanceName = producerId

	// producer group name
	options = append(options, producer.WithGroupName(clientConfig.ProducerGroupName))

	// send msg timeout
	if len(clientConfig.SendMsgTimeout) != 0 {
		sendMsgTimeout, err := strconv.Atoi(clientConfig.SendMsgTimeout)
		if err == nil {
			options = append(options, producer.WithSendMsgTimeout(time.Duration(sendMsgTimeout)*time.Millisecond))
		}
	}

	// producer retry time
	if len(clientConfig.ProducerRetryTimes) != 0 {
		producerRetryTimes, err := strconv.Atoi(clientConfig.ProducerRetryTimes)
		if err == nil {
			options = append(options, producer.WithRetry(producerRetryTimes))
		}
	}

	// producer compress message body threshold
	if len(clientConfig.CompressMsgBodyThreshold) != 0 {
		compressMsgBodyThreshold, err := strconv.Atoi(clientConfig.CompressMsgBodyThreshold)
		if err == nil {
			options = append(options, producer.WithCompressMsgBodyOverHowmuch(compressMsgBodyThreshold))
		}
	}
	return options, nil
}

func getClientConfigFromProperties(properties map[string]string) (*config.ClientConfig, error) {
	arr, err := json.Marshal(properties)
	if err != nil {
		return nil, err
	}
	clientConfig := &config.ClientConfig{}
	err = json.Unmarshal(arr, clientConfig)
	if err != nil {
		return nil, err
	}
	return clientConfig, nil
}
