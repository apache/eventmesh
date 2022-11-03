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

package producer

import (
	"errors"
	"fmt"
	gcommon "github.com/apache/incubator-eventmesh/eventmesh-sdk-go/common"
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/common/protocol/http/common"
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/common/protocol/http/message"
	gutils "github.com/apache/incubator-eventmesh/eventmesh-sdk-go/common/utils"
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/http"
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/http/conf"
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/http/constant"
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/http/model"
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/http/utils"
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/log"
	"time"

	cloudevents "github.com/cloudevents/sdk-go/v2"

	nethttp "net/http"
	"strconv"
)

const bizSeqNoLength = 30
const uniqueIdLen = 30

type CloudEventProducer struct {
	*http.AbstractHttpClient
}

func NewCloudEventProducer(eventMeshHttpClientConfig conf.EventMeshHttpClientConfig) *CloudEventProducer {
	c := &CloudEventProducer{AbstractHttpClient: http.NewAbstractHttpClient(eventMeshHttpClientConfig)}
	return c
}

func (c *CloudEventProducer) Publish(event *cloudevents.Event) error {
	enhancedEvent := c.enhanceCloudEvent(event)
	requestParam := c.buildCommonPostParam(enhancedEvent)
	requestParam.AddHeader(common.ProtocolKey.REQUEST_CODE, strconv.Itoa(common.DefaultRequestCode.MSG_SEND_ASYNC.RequestCode))

	target := c.SelectEventMesh()
	resp := utils.HttpPost(c.HttpClient, target, requestParam)
	var ret http.EventMeshRetObj
	gutils.UnMarshalJsonString(resp, &ret)
	if ret.RetCode != common.DefaultEventMeshRetCode.SUCCESS.RetCode {
		return fmt.Errorf("publish failed, http request error code: %d", ret.RetCode)
	}
	return nil
}

func (c *CloudEventProducer) Request(event *cloudevents.Event, timeout time.Duration) (*cloudevents.Event, error) {
	enhancedEvent := c.enhanceCloudEvent(event)
	requestParam := c.buildCommonPostParam(enhancedEvent)
	requestParam.AddHeader(common.ProtocolKey.REQUEST_CODE, strconv.Itoa(common.DefaultRequestCode.MSG_SEND_SYNC.RequestCode))
	requestParam.SetTimeout(timeout.Milliseconds())
	target := c.SelectEventMesh()
	resp := utils.HttpPost(c.HttpClient, target, requestParam)
	var ret http.EventMeshRetObj
	gutils.UnMarshalJsonString(resp, &ret)
	if ret.RetCode != common.DefaultEventMeshRetCode.SUCCESS.RetCode {
		return nil, fmt.Errorf("request failed, http request code: %d", ret.RetCode)
	}
	retMessage, err := c.transferMessage(&ret)
	if err != nil {
		return nil, err
	}
	return retMessage, nil
}

func (c *CloudEventProducer) transferMessage(retObj *http.EventMeshRetObj) (event *cloudevents.Event, err error) {
	defer func() {
		if r := recover(); r != nil {
			err = errors.New("fail to transfer from EventMeshRetObj to CloudEvent")
		}
	}()
	var message http.ReplyMessage
	gutils.UnMarshalJsonString(retObj.RetMsg, &message)
	retEvent := cloudevents.NewEvent()
	retEvent.SetSubject(message.Topic)
	retEvent.SetData("application/json", []byte(message.Body))
	for k, v := range message.Properties {
		retEvent.SetExtension(k, v)
	}
	return &retEvent, nil
}

func (c *CloudEventProducer) buildCommonPostParam(event *cloudevents.Event) *model.RequestParam {
	eventBytes, err := event.MarshalJSON()
	if err != nil {
		log.Fatalf("Failed to marshal cloudevent")
	}
	content := string(eventBytes)

	requestParam := model.NewRequestParam(nethttp.MethodPost)
	requestParam.AddHeader(common.ProtocolKey.ClientInstanceKey.ENV, c.EventMeshHttpClientConfig.Env())
	requestParam.AddHeader(common.ProtocolKey.ClientInstanceKey.IDC, c.EventMeshHttpClientConfig.Idc())
	requestParam.AddHeader(common.ProtocolKey.ClientInstanceKey.IP, c.EventMeshHttpClientConfig.Ip())
	requestParam.AddHeader(common.ProtocolKey.ClientInstanceKey.PID, c.EventMeshHttpClientConfig.Pid())
	requestParam.AddHeader(common.ProtocolKey.ClientInstanceKey.SYS, c.EventMeshHttpClientConfig.Sys())
	requestParam.AddHeader(common.ProtocolKey.ClientInstanceKey.USERNAME, c.EventMeshHttpClientConfig.UserName())
	requestParam.AddHeader(common.ProtocolKey.ClientInstanceKey.PASSWORD, c.EventMeshHttpClientConfig.Password())
	requestParam.AddHeader(common.ProtocolKey.LANGUAGE, gcommon.Constants.LANGUAGE_GO)
	requestParam.AddHeader(common.ProtocolKey.PROTOCOL_TYPE, constant.CloudEventsProtocol)
	requestParam.AddHeader(common.ProtocolKey.PROTOCOL_DESC, constant.ProtocolDesc)
	requestParam.AddHeader(common.ProtocolKey.PROTOCOL_VERSION, event.SpecVersion())

	// todo: move producerGroup tp header
	requestParam.AddBody(message.SendMessageRequestBodyKey.PRODUCERGROUP, c.EventMeshHttpClientConfig.ProducerGroup())
	requestParam.AddBody(message.SendMessageRequestBodyKey.CONTENT, content)

	return requestParam
}

func (c *CloudEventProducer) enhanceCloudEvent(event *cloudevents.Event) *cloudevents.Event {
	event.SetExtension(common.ProtocolKey.ClientInstanceKey.ENV, c.EventMeshHttpClientConfig.Env())
	event.SetExtension(common.ProtocolKey.ClientInstanceKey.IDC, c.EventMeshHttpClientConfig.Idc())
	event.SetExtension(common.ProtocolKey.ClientInstanceKey.IP, c.EventMeshHttpClientConfig.Ip())
	event.SetExtension(common.ProtocolKey.ClientInstanceKey.PID, c.EventMeshHttpClientConfig.Pid())
	event.SetExtension(common.ProtocolKey.ClientInstanceKey.SYS, c.EventMeshHttpClientConfig.Sys())
	event.SetExtension(common.ProtocolKey.ClientInstanceKey.BIZSEQNO, gutils.RandomNumberStr(bizSeqNoLength))
	event.SetExtension(common.ProtocolKey.ClientInstanceKey.UNIQUEID, gutils.RandomNumberStr(uniqueIdLen))
	event.SetExtension(common.ProtocolKey.LANGUAGE, gcommon.Constants.LANGUAGE_GO)
	event.SetExtension(common.ProtocolKey.PROTOCOL_DESC, fmt.Sprintf("V%s", event.SpecVersion()))
	event.SetExtension(common.ProtocolKey.PROTOCOL_VERSION, event.SpecVersion())
	return event
}
