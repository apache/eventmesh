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
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/config"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/log"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/plugin"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/plugin/protocol"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/runtime/consts"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/runtime/proto/pb"
	cloudv2 "github.com/cloudevents/sdk-go/v2"
	"github.com/liyue201/gostl/ds/set"
	"github.com/liyue201/gostl/ds/vector"
	"github.com/pkg/errors"
	"go.uber.org/atomic"
	"math/rand"
	"sync"
	"time"
)

var (
	ErrNoProtocolFound = errors.New("no protocol type found in event message")
)

type Request struct {
	*Context

	MessageContext *MessageContext
	CreateTime     time.Time
	LastPushTime   time.Time
	Complete       *atomic.Bool
	SimpleMessage  *pb.SimpleMessage
	Try            func() error
}

func NewRequest(mctx *MessageContext) (*Request, error) {
	sm, err := eventToSimpleMessage(mctx.Event)
	if err != nil {
		return nil, err
	}
	return &Request{
		MessageContext: mctx,
		SimpleMessage:  sm,
	}, nil
}

func eventToSimpleMessage(ev *cloudv2.Event) (*pb.SimpleMessage, error) {
	val, ok := ev.Extensions()[consts.PROTOCOL_TYPE]
	if !ok {
		return nil, ErrNoProtocolFound
	}
	ptype := val.(string)
	pplugin := plugin.Get(plugin.Protocol, ptype)
	adapter := pplugin.(protocol.Adapter)
	msg, err := adapter.FromCloudEvent(ev)
	if err != nil {
		return nil, err
	}
	return msg.(*pb.SimpleMessage), nil
}

func (r *Request) timeout() bool {
	return true
}

type StreamRequest struct {
	*Request
	mode     pb.Subscription_SubscriptionItem_SubscriptionMode
	startIdx int
}

func NewStreamRequest(mctx *MessageContext) (*StreamRequest, error) {
	r, err := NewRequest(mctx)
	if err != nil {
		return nil, err
	}
	sr := &StreamRequest{
		Request: r,
	}
	sr.Try = func() error {
		return nil
	}

	return sr, nil
}

type WebhookRequest struct {
	*Request
	// IDCWebhookURLs webhook urls seperated by IDC
	// key is IDC, value is vector.Vector
	IDCWebhookURLs *sync.Map

	// AllURLs all webhook urls, ignore idc
	AllURLs *set.Set

	startIdx int

	subscriptionMode pb.Subscription_SubscriptionItem_SubscriptionMode
}

func NewWebhookRequest(mctx *MessageContext) (*WebhookRequest, error) {
	r, err := NewRequest(mctx)
	if err != nil {
		return nil, err
	}
	rand.Seed(time.Now().UnixMilli())
	hr := &WebhookRequest{
		IDCWebhookURLs:   mctx.TopicConfig.IDCWebhookURLs,
		AllURLs:          mctx.TopicConfig.AllURLs,
		Request:          r,
		startIdx:         rand.Intn(mctx.TopicConfig.AllURLs.Size()),
		subscriptionMode: mctx.SubscriptionMode,
	}
	hr.Try = func() error {
		return nil
	}
	return hr, nil
}

func (w *WebhookRequest) getURLs() []string {
	var (
		urls       []string
		currentIDC = config.GlobalConfig().Server.GRPCOption.IDC
	)

	w.IDCWebhookURLs.Range(func(key, value any) bool {
		idc := key.(string)
		vc := value.(*vector.Vector)
		if idc == currentIDC {
			for iter := vc.Begin(); iter.IsValid(); iter.Next() {
				urls = append(urls, iter.Value().(string))
			}
		}
		return true
	})

	if len(urls) == 0 {
		for iter := w.AllURLs.Begin(); iter.IsValid(); iter.Next() {
			urls = append(urls, iter.Value().(string))
		}
	}
	if len(urls) == 0 {
		log.Warnf("no handler for submitter")
		return []string{}
	}

	switch w.subscriptionMode {
	case pb.Subscription_SubscriptionItem_CLUSTERING:
		return []string{urls[(w.RetryTimes+w.startIdx)%len(urls)]}
	case pb.Subscription_SubscriptionItem_BROADCASTING:
		return urls
	default:
		log.Warnf("invalid Subscription Mode, no message returning back to subscriber")
		return []string{}
	}
}
