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
	"sync"
	"time"
)

var (
	// messageStoreWindow msg ttl,
	// If the currentTimeMills - messageCreateTimeMills >= MESSAGE_STORE_WINDOW,
	// then the message will be clear, default to 1 hour
	messageStoreWindow = time.Hour
)

// Broker used to store event, it just support standalone mode,
// you shouldn't use this module in production environment
type Broker struct {
	// messageContainer store the topic and the queue
	// key = TopicMetadata value = MessageQueue
	messageContainer *sync.Map
	// offsetMap store the offset for topic
	// key = TopicMetadata value = atomic.Long
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
				if now.Sub(currentMsg.createTime) > messageStoreWindow {
					v.(*MessageQueue).RemoveHead()
				}
				return true
			})
		}
	}
}
