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
	"errors"
	"fmt"
	ce "github.com/cloudevents/sdk-go/v2"
	"go.uber.org/atomic"
	"strconv"
	"time"

	"github.com/apache/incubator-eventmesh/eventmesh-server-go/plugin/connector"
)

// Producer standalone producer
type Producer struct {
	broker  *Broker
	started *atomic.Bool
}

func NewProducer() *Producer {
	return &Producer{
		broker:  GetBroker(),
		started: atomic.NewBool(false),
	}
}

func (p *Producer) Publish(ctx context.Context, event *ce.Event, callback connector.SendCallback) (err error) {
	defer func() {
		if r := recover(); r != nil {
			err = fmt.Errorf("callback function execute failed: %v", err)
		}
	}()

	if p.IsClosed() {
		err = errors.New("fail to publish message, producer has been closed")
		return
	}

	message, err := p.broker.PutMessage(event.Subject(), event)
	if err != nil {
		callback.OnError(err)
		return
	}
	sendResult := connector.SendResult{
		MessageId: strconv.FormatInt(message.GetOffset(), 10),
		Topic:     event.Subject(),
		Err:       nil,
	}
	callback.OnSuccess(sendResult)
	return
}

func (p *Producer) SendOneway(ctx context.Context, event *ce.Event) (err error) {
	_, err = p.broker.PutMessage(event.Subject(), event)
	return
}

func (p *Producer) Request(ctx context.Context, event *ce.Event, callback connector.SendCallback, timeout time.Duration) error {
	return fmt.Errorf("request is not supported in standalone connector")
}

func (p *Producer) Reply(ctx context.Context, event *ce.Event, callback connector.SendCallback) error {
	return fmt.Errorf("reply is not supported in standalone connector")
}

func (p *Producer) CheckTopicExist(topicName string) (exist bool, err error) {
	return p.broker.ExistTopic(topicName), nil
}

func (p *Producer) SetExtFields() error {
	// No-Op for standalone producer
	return nil
}

func (p *Producer) InitProducer(properties map[string]string) error {
	// No-Op for standalone producer
	return nil
}

func (p *Producer) Start() error {
	if ok := p.started.CAS(false, true); !ok {
		return errors.New("fail to start standalone producer, producer has already been started")
	}
	return nil
}

func (p *Producer) Shutdown() error {
	if ok := p.started.CAS(true, false); !ok {
		return errors.New("fail to shutdown standalone producer, producer is not started")
	}
	return nil
}

func (p *Producer) IsStarted() bool {
	return p.started.Load()
}

func (p *Producer) IsClosed() bool {
	return !p.started.Load()
}
