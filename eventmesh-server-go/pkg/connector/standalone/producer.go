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
	"fmt"
	"time"

	"github.com/apache/incubator-eventmesh/eventmesh-server-go/pkg/connector"
	cloudevents "github.com/cloudevents/sdk-go/v2"
	"go.uber.org/atomic"
)

var (
	ErrPublishMsg error = fmt.Errorf("publish msg")
)

// Producer producer in standalone
type Producer struct {
	broker    *Broker
	isStarted *atomic.Bool
}

func (p *Producer) internalPublish(ctx context.Context, event *cloudevents.Event) (*connector.SendResult, error) {
	me, err := p.broker.PutMessage(event.Subject(), event)
	if err != nil {
		return nil, err
	}
	return &connector.SendResult{
		Topic:     event.Subject(),
		MessageId: fmt.Sprintf("%v", me.Offset),
	}, nil
}

func (p *Producer) Publish(ctx context.Context, event *cloudevents.Event, callback connector.SendCallback) {
	res, err := p.internalPublish(ctx, event)
	if err != nil {
		callback.OnError(&connector.SendErrResult{
			Err:       err,
			MessageId: event.ID(),
			Topic:     event.Subject(),
		})
		return
	}

	callback.OnSuccess(res)
}

func (p *Producer) SendOneway(ctx context.Context, event *cloudevents.Event) {
	p.internalPublish(ctx, event)
}

func (p *Producer) Request(*cloudevents.Event, connector.RequestReplyCallback, time.Duration) error {
	return fmt.Errorf("un support in standalone connector")
}

func (p *Producer) Reply(*cloudevents.Event, connector.SendCallback) error {
	return fmt.Errorf("un support in standalone connector")
}

func (p *Producer) CheckTopicExist(topicName string) bool {
	return p.broker.CheckTopicExist(topicName)
}

func (p *Producer) SetExtFields() {

}

func (p *Producer) IsStarted() bool {
	return p.isStarted.Load()
}

func (p *Producer) IsClosed() bool {
	return !p.isStarted.Load()
}

func (p *Producer) Start() {
	p.isStarted.CAS(false, true)
}

func (p *Producer) Shutdown() {
	p.isStarted.CAS(true, false)
}
