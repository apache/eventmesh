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
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/log"
	"github.com/pkg/errors"
	"sync"
	"time"
)

var (
	ConsumerGroupWaitingRequestThreshold = 1000

	ErrRequestReachMaxThreshold = errors.New("request reach the max threshold")
)

type MessageHandler struct {
	//pool *ants.Pool
	// waitingRequests waiting to request
	// key to consumerGroup value to []*PushRequest
	waitingRequests *sync.Map
}

func NewMessageHandler(consumerGroup string) (*MessageHandler, error) {
	wr := new(sync.Map)
	// TODO need goroutine safe in []*Request{}
	wr.Store(consumerGroup, []*Request{})
	hdl := &MessageHandler{
		//pool:            p,
		waitingRequests: wr,
	}
	go hdl.checkTimeout()
	return hdl, nil
}

func (m *MessageHandler) checkTimeout() {
	tk := time.NewTicker(time.Second)
	for range tk.C {
		m.waitingRequests.Range(func(key, value interface{}) bool {
			reqs := value.([]*Request)
			for _, req := range reqs {
				if req.timeout() {

				}
			}
			return true
		})
	}
}

func (m *MessageHandler) Handler(mctx *MessageContext) error {
	if m.Size() > ConsumerGroupWaitingRequestThreshold {
		log.Warnf("too many request, reject and send back to MQ, group:%v, threshold:%v",
			mctx.ConsumerGroup, ConsumerGroupWaitingRequestThreshold)
		return ErrRequestReachMaxThreshold
	}

	go func() {
		pushRequest := &Request{}
		if err := pushRequest.Try(); err != nil {

		}
	}()
	return nil
}

func (m *MessageHandler) Size() int {
	count := 0
	m.waitingRequests.Range(func(key, value any) bool {
		count++
		return true
	})
	return count
}
