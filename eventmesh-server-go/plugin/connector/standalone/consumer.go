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
	"sync"

	ce "github.com/cloudevents/sdk-go/v2"
	"github.com/pkg/errors"
	"go.uber.org/atomic"

	"github.com/apache/incubator-eventmesh/eventmesh-server-go/log"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/plugin/connector"
)

type Consumer struct {
	broker          *Broker
	subscribes      map[string]*SubscribeWorker
	committedOffset map[string]*atomic.Int64
	mutex           sync.Mutex
	listener        connector.EventListener
	started         atomic.Bool
}

func NewConsumer() *Consumer {
	return &Consumer{
		broker:          GetBroker(),
		subscribes:      make(map[string]*SubscribeWorker),
		committedOffset: make(map[string]*atomic.Int64),
	}
}

func (c *Consumer) Subscribe(topicName string) error {
	c.mutex.Lock()
	defer c.mutex.Unlock()

	if c.IsClosed() {
		return errors.New("fail to subscribe topic, consumer has been closed")
	}

	if _, ok := c.subscribes[topicName]; !ok {
		err := c.broker.CreateNewQueueIfAbsent(topicName)
		if err != nil {
			return err
		}
		offset := atomic.NewInt64(0)
		worker := &SubscribeWorker{
			broker:    broker,
			topicName: topicName,
			offset:    offset,
			listener:  c.listener,
			quit:      make(chan struct{}, 1),
		}

		c.committedOffset[topicName] = offset
		c.subscribes[topicName] = worker
		go worker.run()
	}
	return nil
}

func (c *Consumer) Unsubscribe(topicName string) error {
	c.mutex.Lock()
	defer c.mutex.Unlock()
	if worker, ok := c.subscribes[topicName]; ok {
		delete(c.subscribes, topicName)
		worker.Stop()
	}
	return nil
}

func (c *Consumer) UpdateOffset(ctx context.Context, events []*ce.Event) error {
	c.mutex.Lock()
	defer c.mutex.Unlock()
	for _, event := range events {
		topicName := event.Subject()
		offset := GetOffsetFromEvent(event)
		if curOffset, ok := c.committedOffset[topicName]; ok {
			if offset <= 0 {
				return fmt.Errorf("fail to update offset, invalid param, topic %s, offset : %d", topicName, offset)
			}
			if offset < curOffset.Load() {
				return nil
			}
			curOffset.Store(offset)
		}
	}
	return nil
}

func (c *Consumer) RegisterEventListener(listener *connector.EventListener) {
	c.listener = *listener
}

func (c *Consumer) InitConsumer(properties map[string]string) error {
	// No-Op for standalone connector
	return nil
}

func (c *Consumer) Start() error {
	c.started.CAS(false, true)
	return nil
}

func (c *Consumer) Shutdown() error {
	c.started.CAS(true, false)
	if ok := c.started.CAS(true, false); ok {
		for topicName, _ := range c.subscribes {
			c.Unsubscribe(topicName)
			delete(c.subscribes, topicName)
		}
	}
	return nil
}

func (c *Consumer) IsStarted() bool {
	return c.started.Load()
}

func (c *Consumer) IsClosed() bool {
	return !c.started.Load()
}

// SubscribeWorker pollMessage from topic and manage consume offset
type SubscribeWorker struct {
	topicName string
	broker    *Broker
	listener  connector.EventListener
	offset    *atomic.Int64
	quit      chan struct{}
}

func (w *SubscribeWorker) run() {
	for {
		select {
		case <-w.quit:
			return
		default:
			err := w.pollMessage()
			if err != nil {
				// retry
				log.Error("[Standalone Consumer] fail to poll message from broker, err=%v", err)
				continue
			}
		}
	}
}

func (w *SubscribeWorker) pollMessage() error {
	var message *Message
	var err error
	if w.offset.Load() == 0 {
		message, err = w.broker.TakeMessage(w.topicName)
		if ok := w.offset.CAS(0, message.GetOffset()); !ok {
			return nil
		}
	} else {
		message, err = w.broker.TakeMessageByOffset(w.topicName, w.offset.Load()+1)
	}
	if err != nil {
		return errors.Wrap(err, "fail to take message from standalone broker")
	}

	commitFunc := func(action connector.EventMeshAction) error {
		switch action {
		case connector.CommitMessage:
			// update offset
			w.offset.Store(message.GetOffset())
		case connector.ReconsumeLater:
			// No-Op
		case connector.ManualAck:
			// update offset
			w.offset.Store(message.GetOffset())
		default:
		}
		return nil
	}

	w.listener.Consume(message.event, commitFunc)
	return nil
}

func (w *SubscribeWorker) Stop() {
	w.quit <- struct{}{}
}
