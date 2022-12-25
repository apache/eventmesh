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

package consumer

import (
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/log"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/runtime/consts"
	"github.com/pkg/errors"
	"sync"
	"time"
)

var (
	ConsumerGroupWaitingRequestThreshold = 1000

	ErrRequestReachMaxThreshold = errors.New("request reach the max threshold")
)

type MessageHandler interface {
	Handler(mctx *MessageContext) error
}

type messageHandler struct {
	//pool *ants.Pool
	// waitingRequests waiting to request
	// key to consumerGroup value to []*PushRequest
	waitingRequests *sync.Map
}

func NewMessageHandler(consumerGroup string) (MessageHandler, error) {
	wr := new(sync.Map)
	// TODO need goroutine safe in []*Request{}
	wr.Store(consumerGroup, []*Request{})
	hdl := &messageHandler{
		//pool:            p,
		waitingRequests: wr,
	}
	go hdl.checkTimeout()
	return hdl, nil
}

func (m *messageHandler) checkTimeout() {
	tk := time.NewTicker(time.Second)
	for range tk.C {
		m.waitingRequests.Range(func(key, value interface{}) bool {
			reqs := value.([]*Request)
			for _, req := range reqs {
				if req.Timeout() {

				}
			}
			return true
		})
	}
}

func (m *messageHandler) Handler(mctx *MessageContext) error {
	if m.Size() > ConsumerGroupWaitingRequestThreshold {
		log.Warnf("too many request, reject and send back to MQ, group:%v, threshold:%v",
			mctx.ConsumerGroup, ConsumerGroupWaitingRequestThreshold)
		return ErrRequestReachMaxThreshold
	}

	var (
		try func() error
	)
	if mctx.GrpcType == consts.WEBHOOK {
		req, err := NewWebhookRequest(mctx)
		if err != nil {
			return err
		}
		try = req.Try
	} else {
		req, err := NewStreamRequest(mctx)
		if err != nil {
			return err
		}
		try = req.Try
	}
	go func() {
		if err := try(); err != nil {
			log.Warnf("failed to handle msg, group:%v, topic:%v, err:%v", mctx.ConsumerGroup, mctx.TopicConfig, err)
		}
	}()
	return nil
}

func (m *messageHandler) Size() int {
	count := 0
	m.waitingRequests.Range(func(key, value any) bool {
		count++
		return true
	})
	return count
}
