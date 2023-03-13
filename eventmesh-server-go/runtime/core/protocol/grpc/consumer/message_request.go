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
	"fmt"
	"io"
	"math/rand"
	"net/http"
	"net/url"
	"sync"
	"time"

	cloudv2 "github.com/cloudevents/sdk-go/v2"
	jsoniter "github.com/json-iterator/go"
	"github.com/liyue201/gostl/ds/set"
	"github.com/pkg/errors"
	"go.uber.org/atomic"

	"github.com/apache/incubator-eventmesh/eventmesh-server-go/config"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/log"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/pkg/common/protocol/grpc"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/pkg/util"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/plugin"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/plugin/protocol"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/runtime/consts"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/runtime/core/protocol/grpc/retry"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/runtime/proto/pb"
)

var (
	ErrNoProtocolFound = errors.New("no protocol type found in event message")

	defaultWebhookTimeout = time.Second * 5

	jsonPool = sync.Pool{New: func() interface{} {
		return jsoniter.Config{
			EscapeHTML: true,
		}.Froze()
	}}
)

type Response struct {
	RetCode string `json:"retCode"`
	ErrMsg  string `json:"errMsg"`
}

type Request struct {
	*retry.Retry

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
	return msg.(*grpc.SimpleMessageWrapper).SimpleMessage, nil
}

func (r *Request) Timeout() bool {
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
	// key is IDC, value is set.Set
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
		IDCWebhookURLs:   mctx.TopicConfig.IDCURLs(),
		AllURLs:          mctx.TopicConfig.AllURLs(),
		Request:          r,
		startIdx:         rand.Intn(mctx.TopicConfig.Size()),
		subscriptionMode: mctx.SubscriptionMode,
	}
	hr.Try = func() error {
		hr.LastPushTime = time.Now()
		httpClient := &http.Client{
			Timeout: defaultWebhookTimeout,
		}
		httpHdr := http.Header{}
		httpHdr.Set(grpc.REQUEST_CODE, grpc.HTTP_PUSH_CLIENT_ASYNC)
		httpHdr.Set(grpc.LANGUAGE, "Go")
		httpHdr.Set(grpc.Version, "1.0")
		httpHdr.Set(grpc.EVENTMESHCLUSTER, config.GlobalConfig().Common.Cluster)
		httpHdr.Set(grpc.EVENTMESHENV, config.GlobalConfig().Common.Env)
		httpHdr.Set(grpc.EVENTMESHIP, util.GetIP())
		httpHdr.Set(grpc.EVENTMESHIDC, config.GlobalConfig().Common.IDC)
		httpHdr.Set(grpc.PROTOCOL_TYPE, hr.SimpleMessage.Header.ProtocolType)
		httpHdr.Set(grpc.PROTOCOL_DESC, hr.SimpleMessage.Header.ProtocolDesc)
		httpHdr.Set(grpc.PROTOCOL_VERSION, hr.SimpleMessage.Header.ProtocolVersion)
		httpHdr.Set(grpc.CONTENT_TYPE, hr.SimpleMessage.Properties[grpc.CONTENT_TYPE])

		formValues := url.Values{}
		formValues.Set(grpc.CONTENT, hr.SimpleMessage.Content)
		formValues.Set(grpc.BIZSEQNO, hr.SimpleMessage.SeqNum)
		formValues.Set(grpc.UNIQUEID, hr.SimpleMessage.UniqueId)
		formValues.Set(grpc.RANDOMNO, mctx.MsgRandomNo)
		formValues.Set(grpc.TOPIC, hr.SimpleMessage.Topic)
		content, _ := jsonPool.Get().(jsoniter.API).MarshalToString(hr.SimpleMessage.Properties)
		formValues.Set(grpc.EXTFIELDS, content)

		// TODO need to clone?
		hr.SimpleMessage.Properties[consts.REQ_EVENTMESH2C_TIMESTAMP] = fmt.Sprintf("%v", hr.LastPushTime.UnixMilli())
		urls := hr.getURLs()
		for _, u := range urls {
			log.Infof("message|eventMesh2client|url={}|topic={}|bizSeqNo={}|uniqueId={}",
				u, hr.SimpleMessage.Topic, hr.SimpleMessage.SeqNum, hr.SimpleMessage.UniqueId)
			resp, err := httpClient.PostForm(u, formValues)
			if err != nil {
				log.Warnf("err:%v in submit to url:%v|topic={}|bizSeqNo={}|uniqueId={}",
					err, u, hr.SimpleMessage.Topic, hr.SimpleMessage.SeqNum, hr.SimpleMessage.UniqueId)
				continue
			}
			if resp.StatusCode != http.StatusOK {
				log.Warnf("status code:%v to submit to url:%v|topic={}|bizSeqNo={}|uniqueId={}",
					resp.StatusCode, hr.SimpleMessage.Topic, hr.SimpleMessage.SeqNum, hr.SimpleMessage.UniqueId)
				continue
			}
			buf, err := io.ReadAll(resp.Body)
			if err != nil {
				log.Warnf("err:%v in read response url:%v|topic={}|bizSeqNo={}|uniqueId={}",
					err, hr.SimpleMessage.Topic, hr.SimpleMessage.SeqNum, hr.SimpleMessage.UniqueId)
				continue
			}
			res := &Response{}
			if err := jsonPool.Get().(jsoniter.API).Unmarshal(buf, res); err != nil {
				log.Warnf("err:%v in unmarshal response:%v url:%v|topic={}|bizSeqNo={}|uniqueId={}",
					err, string(buf), hr.SimpleMessage.Topic, hr.SimpleMessage.SeqNum, hr.SimpleMessage.UniqueId)
				continue
			}
		}
		return nil
	}
	return hr, nil
}

func (w *WebhookRequest) getURLs() []string {
	var (
		urls       []string
		currentIDC = config.GlobalConfig().Common.IDC
	)

	w.IDCWebhookURLs.Range(func(key, value any) bool {
		idc := key.(string)
		vc := value.(*set.Set)
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
