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
	cloudevents "github.com/cloudevents/sdk-go/v2"
	"go.uber.org/atomic"
	"sync"
	"time"
)

var (
	// messageStoreWindow msg ttl,
	// If the currentTimeMills - messageCreateTimeMills >= MESSAGE_STORE_WINDOW,
	// then the message will be clear, default to 1 hour
	messageStoreWindow = time.Hour

	// ErrTopicNotExist topic queue not exist in the broker
	ErrTopicNotExist = fmt.Errorf("topic queue not exist")
)

// Broker used to store event, it just support standalone mode,
// you shouldn't use this module in production environment
type Broker struct {
	// messageContainer store the topic and the queue
	// key = topicName value = MessageQueue
	messageContainer *sync.Map
	// offsetMap store the offset for topic
	// key = topicName value = atomic.Long
	offsetMap *sync.Map
}

// NewBroker create new standalone broker
func NewBroker(ctx context.Context) *Broker {
	b := &Broker{
		messageContainer: new(sync.Map),
		offsetMap:        new(sync.Map),
	}
	go b.startHistoryMessageCleanTask(ctx)
	return b
}

// startHistoryMessageCleanTask clean the message which has pass the window
func (b *Broker) startHistoryMessageCleanTask(ctx context.Context) {
	cleanTicker := time.NewTicker(time.Second)
	for {
		select {
		case <-ctx.Done():
			return
		case <-cleanTicker.C:
			b.messageContainer.Range(func(k, v interface{}) bool {
				now := time.Now()
				currentMsg := v.(*MessageQueue).GetHead()
				if currentMsg == nil {
					return true
				}
				if now.Sub(currentMsg.CreateTime) > messageStoreWindow {
					v.(*MessageQueue).RemoveHead()
				}
				return true
			})
		}
	}
}

// PutMessage put message into broker
func (b *Broker) PutMessage(topicName string, event *cloudevents.Event) (*MessageEntity, error) {
	queue, offset := b.createTopicIfAbsent(topicName)
	msg := &MessageEntity{
		TopicMetadata: &TopicMetadata{
			TopicName: topicName,
		},
		Message:    event,
		Offset:     offset.Inc(),
		CreateTime: time.Now(),
	}
	return msg, queue.Put(msg)
}

// TakeMessage Get the message, if the queue is empty then await
func (b *Broker) TakeMessage(topicName string) (*cloudevents.Event, error) {
	val, ok := b.messageContainer.Load(topicName)
	if !ok {
		return nil, ErrTopicNotExist
	}
	queue := val.(*MessageQueue)

	return queue.Take().Message, nil
}

// GetMessage return the message in the head
func (b *Broker) GetMessage(topicName string) (*cloudevents.Event, error) {
	return b.GetMessageByOffset(topicName, 0)
}

// GetMessageByOffset get the message according to the offset
// if offset is zero, head message will return
func (b *Broker) GetMessageByOffset(topicName string, offset int64) (*cloudevents.Event, error) {
	val, ok := b.messageContainer.Load(topicName)
	if !ok {
		return nil, ErrTopicNotExist
	}
	queue := val.(*MessageQueue)

	if offset == 0 {
		return queue.GetHead().Message, nil
	}

	msg, err := queue.GetByOffset(offset)
	if err != nil {
		return nil, err
	}
	return msg.Message, nil
}

// CheckTopicExist check the topic is exist in the broker
func (b *Broker) CheckTopicExist(topicName string) bool {
	_, ok := b.messageContainer.Load(topicName)
	return ok
}

// UpdateOffset update the topic offset
func (b *Broker) UpdateOffset(topicName string, offset int64) error {
	val, ok := b.offsetMap.Load(topicName)
	if !ok {
		return ErrTopicNotExist
	}

	af := val.(*atomic.Int64)
	af.Store(offset)
	return nil
}

// createTopicIfAbsent create the message queue and offset if not exist
func (b *Broker) createTopicIfAbsent(topicName string) (*MessageQueue, *atomic.Int64) {
	var (
		offset *atomic.Int64
		queue  *MessageQueue
	)

	val, ok := b.messageContainer.Load(topicName)
	if !ok {
		queue = NewDefaultMessageQueue()
		b.messageContainer.Store(topicName, queue)
	} else {
		queue = val.(*MessageQueue)
	}

	valoffset, ok := b.offsetMap.Load(topicName)
	if !ok {
		offset = atomic.NewInt64(0)
		b.offsetMap.Store(topicName, offset)
	} else {
		offset = (valoffset).(*atomic.Int64)
	}

	return queue, offset
}
