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
	"errors"
	"fmt"
	"sync"
	"time"

	ce "github.com/cloudevents/sdk-go/v2"
	"go.uber.org/atomic"
)

const (
	defaultQueueSize   = 1024
	defaultExpireMills = 60 * 60 * 1000
)

// MessageQueue message storage of standalone broker
type MessageQueue struct {
	capacity int
	items    []*Message
	mutex    sync.Mutex
	notFull  sync.Cond
	newMsg   sync.Cond
}

func NewMessageQueue() (*MessageQueue, error) {
	return NewMessageQueueWithCapacity(defaultQueueSize)
}

func NewMessageQueueWithCapacity(capacity int) (*MessageQueue, error) {
	if capacity <= 0 {
		return nil, errors.New("fail to create message queue: capacity must be positive")
	}

	queue := &MessageQueue{
		capacity: capacity,
		items:    make([]*Message, 0, capacity),
	}
	queue.notFull = sync.Cond{L: &queue.mutex}
	queue.newMsg = sync.Cond{L: &queue.mutex}
	return queue, nil
}

func (q *MessageQueue) Put(message *Message) {
	q.mutex.Lock()
	defer q.mutex.Unlock()

	// Wait util queue is not null
	for q.capacity == len(q.items) {
		q.notFull.Wait()
	}
	q.items = append(q.items, message)
	q.newMsg.Signal()
}

// Get fetch message in top of queue, block if message queue is empty
func (q *MessageQueue) Get() (*Message, error) {
	q.mutex.Lock()
	defer q.mutex.Unlock()

	// Wait util queue is not empty
	for len(q.items) == 0 {
		q.newMsg.Wait()
	}
	return q.items[0], nil
}

// GetIfNotEmpty fetch message in top of queue, return nil if message queue is empty
func (q *MessageQueue) GetIfNotEmpty() *Message {
	q.mutex.Lock()
	defer q.mutex.Unlock()

	if len(q.items) > 0 {
		return q.items[0]
	}
	return nil
}

// GetByOffset fetch message of curtain offset
// return error if message has been deleted, block if message of that offset is not available currently
func (q *MessageQueue) GetByOffset(offset int64) (*Message, error) {
	q.mutex.Lock()
	defer q.mutex.Unlock()

	if offset < 0 {
		return nil, fmt.Errorf("invalid offset param: %d", offset)
	}

	for {
		// Wait util queue is not empty
		if len(q.items) == 0 {
			q.newMsg.Wait()
			continue
		}

		lastMessage := q.items[len(q.items)-1]
		// Wait util new message has been put
		if lastMessage.GetOffset() < offset {
			q.newMsg.Wait()
			continue
		}

		firstMessage := q.items[0]
		// message has been deleted
		if firstMessage.GetOffset() > offset {
			return nil, fmt.Errorf("offset has been deleted, offset : %d", offset)
		}

		index := int(offset - firstMessage.GetOffset())
		return q.items[index], nil
	}
}

// PopMessage remove message in the top, return nil if message queue is empty
func (q *MessageQueue) PopMessage() *Message {
	q.mutex.Lock()
	defer q.mutex.Unlock()
	if len(q.items) != 0 {
		message := q.items[0]
		q.items = q.items[1:]
		return message
	}
	return nil
}

func (q *MessageQueue) Size() int {
	return len(q.items)
}

// Broker a manager of different topic message queue
type Broker struct {
	// <topicName, messageQueue>
	queueContainer map[string]*MessageQueue
	// <topicName, offset>
	offsetContainer map[string]*atomic.Int64
	// <topicName, worker>
	expireWorkers map[string]*MessageExpireWorker
}

var initOnce sync.Once
var broker *Broker

// GetBroker all topic shared same broker
func GetBroker() *Broker {
	initOnce.Do(func() {
		broker = &Broker{
			queueContainer:  make(map[string]*MessageQueue),
			offsetContainer: make(map[string]*atomic.Int64),
			expireWorkers:   make(map[string]*MessageExpireWorker),
		}
	},
	)
	return broker
}

func (b *Broker) TakeMessage(topicName string) (*Message, error) {
	if err := b.CreateNewQueueIfAbsent(topicName); err != nil {
		return nil, err
	}
	return b.queueContainer[topicName].Get()
}

func (b *Broker) TakeMessageByOffset(topicName string, offset int64) (*Message, error) {
	if err := b.CreateNewQueueIfAbsent(topicName); err != nil {
		return nil, err
	}
	return b.queueContainer[topicName].GetByOffset(offset)
}

func (b *Broker) PutMessage(topicName string, event *ce.Event) (message *Message, err error) {
	if err = b.CreateNewQueueIfAbsent(topicName); err != nil {
		return
	}
	message = &Message{
		createTimeMills: time.Now().UnixMilli(),
		event:           event,
	}
	message.SetOffset(b.offsetContainer[topicName].Add(1))
	b.queueContainer[topicName].Put(message)
	return
}

func (b *Broker) CreateNewQueueIfAbsent(topicName string) (err error) {
	if _, ok := b.queueContainer[topicName]; ok {
		return
	}
	queue, err := NewMessageQueue()
	if err != nil {
		return
	}

	offset := atomic.NewInt64(0)
	b.queueContainer[topicName] = queue
	b.offsetContainer[topicName] = offset

	expireWorker := &MessageExpireWorker{
		messageQueue: queue,
		quit:         make(chan struct{}, 1),
		expireMills:  defaultExpireMills,
	}
	b.expireWorkers[topicName] = expireWorker
	go expireWorker.schedule(5 * time.Second)
	return
}

func (b *Broker) ExistTopic(topicName string) (exist bool) {
	_, exist = b.queueContainer[topicName]
	return
}

// MessageExpireWorker periodically evict expire message
type MessageExpireWorker struct {
	messageQueue *MessageQueue
	quit         chan struct{}
	expireMills  int64
}

func (w *MessageExpireWorker) schedule(interval time.Duration) {
	ticket := time.NewTicker(interval)
	for {
		select {
		case <-ticket.C:
			w.doEvict()
		case <-w.quit:
			ticket.Stop()
			return
		}
	}
}

func (w *MessageExpireWorker) doEvict() {
	message := w.messageQueue.GetIfNotEmpty()
	for message != nil {
		if message.createTimeMills+w.expireMills < time.Now().UnixMilli() {
			w.messageQueue.PopMessage()
		}
		message = w.messageQueue.GetIfNotEmpty()
	}
}

func (w *MessageExpireWorker) stop() {
	w.quit <- struct{}{}
}
