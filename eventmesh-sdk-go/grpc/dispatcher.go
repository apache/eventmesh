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

package grpc

import (
	"fmt"
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/grpc/proto"
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/log"
	"sync"
	"time"

	"github.com/panjf2000/ants"
)

var (
	// ErrTopicDispatcherExist repeated dispatcher for topic
	ErrTopicDispatcherExist = fmt.Errorf("already exist dispatcher for given topic")
)

// pooledHandler internal handler for subscribe message with goroutine pool
type pooledHandler struct {
	*ants.Pool
	handler OnMessage
	timeout time.Duration
}

// OnMessage redirect the msg with pool
func (p *pooledHandler) OnMessage(msg *proto.SimpleMessage) interface{} {
	ch := make(chan interface{})
	if err := p.Submit(func() {
		m := *msg
		ch <- p.handler(&m)
	}); err != nil {
		log.Warnf("submit msg to pool err:%v, msgID:%v", err, msg.UniqueId)
		return err
	}
	select {
	case <-time.After(p.timeout):
		log.Warnf("timeout in wait the response, msgID:%v", msg.UniqueId)
		break
	case val, ok := <-ch:
		if !ok {
			log.Warnf("wait response, msg chan closed, msgID:%v", msg.UniqueId)
		} else {
			if val != nil {
				log.Infof("reply for msg:%v", msg.UniqueId)
				return val
			}
		}
	}
	return nil
}

// messageDispatcher dispatch the message to different handler according to
// it's topic
type messageDispatcher struct {
	// topicMap key is the topic name, value is the SubscribeMessageHandler
	topicMap *sync.Map
	// poolSize concurrent for dispatch received msg
	poolsize int
	// timeout to process on message
	timeout time.Duration
}

// newMessageDispatcher create new message dispatcher
func newMessageDispatcher(ps int, tm time.Duration) *messageDispatcher {
	return &messageDispatcher{
		topicMap: new(sync.Map),
		poolsize: ps,
		timeout:  tm,
	}
}

// addHandler add msg handler
func (m *messageDispatcher) addHandler(topic string, hdl OnMessage) error {
	_, ok := m.topicMap.Load(topic)
	if ok {
		return ErrTopicDispatcherExist
	}
	pool, err := ants.NewPool(m.poolsize, ants.WithPanicHandler(func(i interface{}) {
		log.Warnf("process message failure, err:%v", i)
	}))
	if err != nil {
		return err
	}
	m.topicMap.Store(topic, &pooledHandler{
		Pool:    pool,
		handler: hdl,
		timeout: m.timeout,
	})
	return nil
}

// OnMessage dispatch the message by topic
func (m *messageDispatcher) onMessage(msg *proto.SimpleMessage) (interface{}, error) {
	// subscribe response ignore it
	if msg.Topic == "" {
		return nil, nil
	}
	val, ok := m.topicMap.Load(msg.Topic)
	if !ok {
		log.Warnf("no dispatch found for topic:%s, drop msg", msg.Topic)
		return nil, ErrTopicDispatcherExist
	}
	ph := val.(*pooledHandler)
	return ph.OnMessage(msg), nil
}
