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
	"github.com/apache/rocketmq-client-go/v2/primitive"
	ce "github.com/cloudevents/sdk-go/v2"
	"strings"
	"sync"
	"time"
)

type SendCallback func(ctx context.Context, result *primitive.SendResult, err error)

type Producer struct {
	rocketMQProducer client.RocketMQProducer
	started          bool
	mutex            sync.Mutex
}

// NewProducer get new producer, needs to be Initiated before using
func NewProducer() *Producer {
	return &Producer{}
}

// InitProducer init producer by properties
func (p *Producer) InitProducer(properties map[string]string) error {
	producer, err := client.NewRocketMQProducerWrapper(properties)
	if err != nil {
		return err
	}
	p.rocketMQProducer = producer
	return nil
}

// IsStarted check if producer is started
func (p *Producer) IsStarted() bool {
	return p.rocketMQProducer != nil && p.started
}

// IsClosed check if producer is closed
func (p *Producer) IsClosed() bool {
	return p.rocketMQProducer != nil && !p.started
}

// Start make producer started
func (p *Producer) Start() error {
	if p.rocketMQProducer == nil {
		return errors.New("start rocketmq producer fail, producer should be initiated first")
	}
	p.mutex.Lock()
	defer p.mutex.Unlock()
	if !p.started {
		err := p.rocketMQProducer.Start()
		if err != nil {
			return err
		}
		p.started = true
	}
	return nil
}

// Shutdown terminate the producer
func (p *Producer) Shutdown() error {
	if p.rocketMQProducer == nil {
		return errors.New("shutdown rocketmq producer fail, producer should be initiated first")
	}
	p.mutex.Lock()
	defer p.mutex.Unlock()
	if p.started {
		err := p.rocketMQProducer.Shutdown()
		if err != nil {
			return err
		}
		p.started = false
	}
	return nil
}

// Publish async publish message to broker
func (p *Producer) Publish(ctx context.Context, event *ce.Event, callback *connector.SendCallback) error {
	if err := p.checkProducerStatus(); err != nil {
		return err
	}
	msg, err := convert.NewRocketMQMessageWriter(event.Subject()).ToMessage(ctx, event)
	if err != nil {
		return err
	}
	p.supplySysProp(msg, event)
	err = p.rocketMQProducer.SendAsync(ctx, p.sendCallbackConvert(callback), msg)
	if err != nil {
		return err
	}
	return nil
}

// SendOneway async send message without callback
func (p *Producer) SendOneway(ctx context.Context, event *ce.Event) error {
	if err := p.checkProducerStatus(); err != nil {
		return err
	}
	msg, err := convert.NewRocketMQMessageWriter(event.Subject()).ToMessage(ctx, event)
	if err != nil {
		return err
	}
	p.supplySysProp(msg, event)
	err = p.rocketMQProducer.SendOneWay(ctx, msg)
	if err != nil {
		return err
	}
	return nil
}

// Request async request message
func (p *Producer) Request(ctx context.Context, event *ce.Event, callback *connector.RequestReplyCallback,
	timeout time.Duration) error {
	if err := p.checkProducerStatus(); err != nil {
		return err
	}
	msg, err := convert.NewRocketMQMessageWriter(event.Subject()).ToMessage(ctx, event)
	if err != nil {
		return err
	}
	p.supplySysProp(msg, event)
	return p.rocketMQProducer.RequestAsync(ctx, timeout, p.requestReplyCallbackConvert(event.Subject(), callback), msg)
}

// Reply async send message to reply
func (p *Producer) Reply(ctx context.Context, event *ce.Event, callback *connector.SendCallback) error {
	if err := p.checkProducerStatus(); err != nil {
		return err
	}
	msg, err := convert.NewRocketMQMessageWriter(event.Subject()).ToMessage(ctx, event)
	if err != nil {
		return err
	}
	p.supplySysProp(msg, event)
	err = p.rocketMQProducer.SendAsync(ctx, p.sendCallbackConvert(callback))
	if err != nil {
		return err
	}
	return nil
}

// CheckTopicExist RocketMQ go-sdk doesn't support topic check
func (p *Producer) CheckTopicExist(topicName string) (bool, error) {
	// RocketMQ go-sdk doesn't support topic check
	return false, errors.New("rocketmq producer doesn't support topic check")
}

// SetExtFields do nothing, RocketMQ go-sdk doesn't support dynamic client option modify
func (p *Producer) SetExtFields() error {
	return nil
}

// checkProducerStatus check is the producer has started
func (p *Producer) checkProducerStatus() error {
	if p.rocketMQProducer == nil {
		return errors.New("rocketmq producer has not been initiated")
	}

	if !p.started {
		return errors.New("rocketmq producer has not started")
	}
	return nil
}

// supplySysProp convert producer client's system properties format
func (p *Producer) supplySysProp(message *primitive.Message, cloudEvent *ce.Event) {
	for _, propertyKey := range constants.RocketMQMessageProperties.ToSlice() {
		key := strings.ReplaceAll(strings.ToLower(propertyKey), "_", constants.MessagePropertySeparator)
		if val, ok := cloudEvent.Extensions()[key]; ok {
			message.WithProperty(propertyKey, val.(string))
			message.RemoveProperty(key)
		}
	}
}

// sendCallbackConvert convert connector API callback to RocketMQ callback
func (p *Producer) sendCallbackConvert(callback *connector.SendCallback) SendCallback {
	return func(ctx context.Context, result *primitive.SendResult, err error) {
		if err != nil {
			callback.OnError(&connector.ErrorResult{
				Topic: result.MessageQueue.Topic,
				Err:   err,
			})
		}
		callback.OnSuccess(p.convertToSendResult(result))
	}
}

// requestReplyCallbackConvert convert connector API callback to RocketMQ callback
func (p *Producer) requestReplyCallbackConvert(topic string,
	callback *connector.RequestReplyCallback) func(ctx context.Context, msg *primitive.Message, err error) {
	return func(ctx context.Context, msg *primitive.Message, err error) {
		if err != nil {
			callback.OnError(&connector.ErrorResult{
				Topic: topic,
				Err:   err,
			})
			return
		}
		convert.TransferMessageSystemProperties(msg)
		event, err := convert.NewRocketMQMessageReader(msg).ToCloudEvent(ctx)
		if err != nil {
			panic(err)
		}
		callback.OnSuccess(event)
	}
}

func (p *Producer) convertToSendResult(result *primitive.SendResult) *connector.SendResult {
	return &connector.SendResult{
		MessageId: result.MsgID,
		Topic:     result.MessageQueue.Topic,
	}
}
