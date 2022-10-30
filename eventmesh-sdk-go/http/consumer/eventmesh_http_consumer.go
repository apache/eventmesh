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
	gcommon "github.com/apache/incubator-eventmesh/eventmesh-sdk-go/common"
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/common/protocol"
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/common/protocol/http/body/client"
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/common/protocol/http/common"
	gutils "github.com/apache/incubator-eventmesh/eventmesh-sdk-go/common/utils"
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/http"
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/http/conf"
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/http/model"
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/http/utils"
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/log"
	nethttp "net/http"
	"strconv"
	"sync"
	"time"
)

type EventMeshHttpConsumer struct {
	*http.AbstractHttpClient
	subscriptions []protocol.SubscriptionItem
	mutex         sync.Mutex
}

func NewEventMeshHttpConsumer(eventMeshHttpClientConfig conf.EventMeshHttpClientConfig) *EventMeshHttpConsumer {
	c := &EventMeshHttpConsumer{AbstractHttpClient: http.NewAbstractHttpClient(eventMeshHttpClientConfig)}
	c.subscriptions = make([]protocol.SubscriptionItem, 0, 1000)
	return c
}

func (e *EventMeshHttpConsumer) HeartBeat(topicList []protocol.SubscriptionItem, subscribeUrl string) {
	if len(topicList) == 0 || len(subscribeUrl) == 0 {
		return
	}

	for range time.Tick(time.Duration(gcommon.Constants.HEARTBEAT) * time.Millisecond) {
		var heartbeatEntities []client.HeartbeatEntity
		for _, item := range topicList {
			entity := client.HeartbeatEntity{Topic: item.Topic, Url: subscribeUrl}
			heartbeatEntities = append(heartbeatEntities, entity)
		}

		requestParam := e.buildCommonRequestParam()
		requestParam.AddHeader(common.ProtocolKey.REQUEST_CODE, strconv.Itoa(common.DefaultRequestCode.HEARTBEAT.RequestCode))
		requestParam.AddBody(client.HeartbeatRequestBodyKey.CLIENTTYPE, common.DefaultClientType.SUB.Name)
		requestParam.AddBody(client.HeartbeatRequestBodyKey.CLIENTTYPE, "SUB")
		requestParam.AddBody(client.HeartbeatRequestBodyKey.HEARTBEATENTITIES, gutils.MarshalJsonString(heartbeatEntities))

		target := e.SelectEventMesh()
		resp := utils.HttpPost(e.HttpClient, target, requestParam)
		var ret http.EventMeshRetObj
		gutils.UnMarshalJsonString(resp, &ret)
		if ret.RetCode != common.DefaultEventMeshRetCode.SUCCESS.RetCode {
			log.Fatalf("Request failed, error code: %d", ret.RetCode)
		}
	}

}

func (e *EventMeshHttpConsumer) Subscribe(topicList []protocol.SubscriptionItem, subscribeUrl string) {
	if len(topicList) == 0 || len(subscribeUrl) == 0 {
		return
	}

	requestParam := e.buildCommonRequestParam()
	requestParam.AddHeader(common.ProtocolKey.REQUEST_CODE, strconv.Itoa(common.DefaultRequestCode.SUBSCRIBE.RequestCode))
	requestParam.AddBody(client.SubscribeRequestBodyKey.TOPIC, gutils.MarshalJsonString(topicList))
	requestParam.AddBody(client.SubscribeRequestBodyKey.URL, subscribeUrl)
	requestParam.AddBody(client.SubscribeRequestBodyKey.CONSUMERGROUP, e.EventMeshHttpClientConfig.ConsumerGroup())

	target := e.SelectEventMesh()
	resp := utils.HttpPost(e.HttpClient, target, requestParam)
	var ret http.EventMeshRetObj
	gutils.UnMarshalJsonString(resp, &ret)
	if ret.RetCode != common.DefaultEventMeshRetCode.SUCCESS.RetCode {
		log.Fatalf("Request failed, error code: %d", ret.RetCode)
	}

	e.mutex.Lock()
	defer e.mutex.Unlock()
	e.subscriptions = append(e.subscriptions, topicList...)
}

func (e *EventMeshHttpConsumer) Unsubscribe(topicList []string, subscribeUrl string) {
	if len(topicList) == 0 || len(subscribeUrl) == 0 {
		return
	}
	requestParam := e.buildCommonRequestParam()
	requestParam.AddHeader(common.ProtocolKey.REQUEST_CODE, strconv.Itoa(common.DefaultRequestCode.UNSUBSCRIBE.RequestCode))
	requestParam.AddBody(client.SubscribeRequestBodyKey.TOPIC, gutils.MarshalJsonString(topicList))
	requestParam.AddBody(client.SubscribeRequestBodyKey.URL, subscribeUrl)
	target := e.SelectEventMesh()
	resp := utils.HttpPost(e.HttpClient, target, requestParam)
	var ret http.EventMeshRetObj
	gutils.UnMarshalJsonString(resp, &ret)
	if ret.RetCode != common.DefaultEventMeshRetCode.SUCCESS.RetCode {
		log.Fatalf("Request failed, error code: %d", ret.RetCode)
	}

	e.mutex.Lock()
	defer e.mutex.Unlock()
	topicSet := make(map[string]struct{})
	for _, topic := range topicList {
		topicSet[topic] = struct{}{}
	}

	subscriptions := make([]protocol.SubscriptionItem, 0, len(e.subscriptions))
	for _, sub := range e.subscriptions {
		if _, ok := topicSet[sub.Topic]; !ok {
			subscriptions = append(subscriptions, sub)
		}
	}
	e.subscriptions = subscriptions
}

func (e *EventMeshHttpConsumer) buildCommonRequestParam() *model.RequestParam {
	param := model.NewRequestParam(nethttp.MethodPost)
	param.AddHeader(common.ProtocolKey.ClientInstanceKey.ENV, e.EventMeshHttpClientConfig.Env())
	param.AddHeader(common.ProtocolKey.ClientInstanceKey.IDC, e.EventMeshHttpClientConfig.Idc())
	param.AddHeader(common.ProtocolKey.ClientInstanceKey.IP, e.EventMeshHttpClientConfig.Ip())
	param.AddHeader(common.ProtocolKey.ClientInstanceKey.PID, e.EventMeshHttpClientConfig.Pid())
	param.AddHeader(common.ProtocolKey.ClientInstanceKey.SYS, e.EventMeshHttpClientConfig.Sys())
	param.AddHeader(common.ProtocolKey.ClientInstanceKey.USERNAME, e.EventMeshHttpClientConfig.UserName())
	param.AddHeader(common.ProtocolKey.ClientInstanceKey.PASSWORD, e.EventMeshHttpClientConfig.Password())
	param.AddHeader(common.ProtocolKey.VERSION, common.DefaultProtocolVersion.V1.Version())
	param.AddHeader(common.ProtocolKey.LANGUAGE, gcommon.Constants.LANGUAGE_GO)
	param.SetTimeout(gcommon.Constants.DEFAULT_HTTP_TIME_OUT)
	param.AddBody(client.HeartbeatRequestBodyKey.CONSUMERGROUP, e.EventMeshHttpClientConfig.ConsumerGroup())
	return param
}
