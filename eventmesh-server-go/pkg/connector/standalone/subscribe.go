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
	"time"

	"github.com/apache/incubator-eventmesh/eventmesh-server-go/log"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/pkg/connector"
	"go.uber.org/atomic"
)

// ExtensionOffset key for extention about offset in cloudevent
var ExtensionOffset = "offset"

// SubscribeProcessor procese to handle the subscribe message
type SubscribeProcessor struct {
	topicName string
	broker    *Broker
	listner   connector.EventListener
	offset    *atomic.Int64
	ctx       context.Context
	cancel    context.CancelFunc
}

// NewSubscribeProcessor create new topic subscribe processer
// and start a loop to handle the message
func NewSubscribeProcessor(ctx context.Context, topicName string, br *Broker, lis connector.EventListener) *SubscribeProcessor {
	cctx, cancel := context.WithCancel(ctx)
	s := &SubscribeProcessor{
		topicName: topicName,
		broker:    br,
		listner:   lis,
		ctx:       cctx,
		cancel:    cancel,
	}
	go s.RunLoop()
	return s
}

// RunLoop run loop to process message
func (s *SubscribeProcessor) RunLoop() {
	log.Infof("process subscribe for topic:%s", s.topicName)

	tick := time.NewTicker(time.Second)
	for {
		select {
		case <-s.ctx.Done():
			return
		case <-tick.C:
			if s.offset == nil {
				msg, err := s.broker.GetMessage(s.topicName)
				if err != nil {
					log.Warnf("get message from broker err:%v, topic:%v", err, s.topicName)
					break
				}
				if msg != nil {
					if val, ok := msg.Extensions()[ExtensionOffset]; ok {
						s.offset = atomic.NewInt64(val.(int64))
					} else {
						s.offset = atomic.NewInt64(0)
					}
				}
			}

			msg, err := s.broker.GetMessageByOffset(s.topicName, s.offset.Load())
			if err != nil {
				log.Warnf("get message by offset from broker err:%v, topic:%v", err, s.topicName)
				break
			}
			s.listner.Consume(msg, func(ac connector.EventMeshAction) {
				switch ac {
				case connector.CommitMessage:
					log.Infof("commit message:%v, topic:%s, offset:%v", msg.ID(), s.topicName, s.offset.Load())
				case connector.ManualAck:
					log.Infof("manual message:%v, topic:%s, offset:%v", msg.ID(), s.topicName, s.offset.Load())
				case connector.ReconsumeLater:
					log.Infof("ack message:%v, topic:%s, offset:%v", msg.ID(), s.topicName, s.offset.Inc())
				}
			})
		}
	}
}

// Shutdown
func (s *SubscribeProcessor) Shutdown() {
	log.Infof("shutdown subscribe processor, topic:%s", s.topicName)
	s.cancel()
}
