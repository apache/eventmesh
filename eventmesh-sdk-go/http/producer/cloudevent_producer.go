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
	gcommon "github.com/apache/incubator-eventmesh/eventmesh-sdk-go/common"
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/common/protocol/http/common"
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/common/protocol/http/message"
	gutils "github.com/apache/incubator-eventmesh/eventmesh-sdk-go/common/utils"
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/http"
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/http/conf"
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/http/model"
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/http/utils"
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/log"
	cloudevents "github.com/cloudevents/sdk-go/v2"

	nethttp "net/http"
	"strconv"
)

type CloudEventProducer struct {
	*http.AbstractHttpClient
}

func NewCloudEventProducer(eventMeshHttpClientConfig conf.EventMeshHttpClientConfig) *CloudEventProducer {
	c := &CloudEventProducer{AbstractHttpClient: http.NewAbstractHttpClient(eventMeshHttpClientConfig)}
	return c
}

func (c *CloudEventProducer) Publish(event cloudevents.Event) {
	enhancedEvent := c.enhanceCloudEvent(event)
	requestParam := c.buildCommonPostParam(enhancedEvent)
	requestParam.AddHeader(common.ProtocolKey.REQUEST_CODE, strconv.Itoa(common.DefaultRequestCode.MSG_SEND_ASYNC.RequestCode))

	target := c.SelectEventMesh()
	resp := utils.HttpPost(c.HttpClient, target, requestParam)
	var ret http.EventMeshRetObj
	gutils.UnMarshalJsonString(resp, &ret)
	if ret.RetCode != common.DefaultEventMeshRetCode.SUCCESS.RetCode {
		log.Fatalf("Request failed, error code: %d", ret.RetCode)
	}
}

func (c *CloudEventProducer) buildCommonPostParam(event cloudevents.Event) *model.RequestParam {

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
	// FIXME Improve constants
	requestParam.AddHeader(common.ProtocolKey.PROTOCOL_TYPE, "cloudevents")
	requestParam.AddHeader(common.ProtocolKey.PROTOCOL_DESC, "http")
	requestParam.AddHeader(common.ProtocolKey.PROTOCOL_VERSION, event.SpecVersion())

	// todo: move producerGroup tp header
	requestParam.AddBody(message.SendMessageRequestBodyKey.PRODUCERGROUP, c.EventMeshHttpClientConfig.ProducerGroup())
	requestParam.AddBody(message.SendMessageRequestBodyKey.CONTENT, content)

	return requestParam
}

func (c *CloudEventProducer) enhanceCloudEvent(event cloudevents.Event) cloudevents.Event {
	event.SetExtension(common.ProtocolKey.ClientInstanceKey.ENV, c.EventMeshHttpClientConfig.Env())
	event.SetExtension(common.ProtocolKey.ClientInstanceKey.IDC, c.EventMeshHttpClientConfig.Idc())
	event.SetExtension(common.ProtocolKey.ClientInstanceKey.IP, c.EventMeshHttpClientConfig.Ip())
	event.SetExtension(common.ProtocolKey.ClientInstanceKey.PID, c.EventMeshHttpClientConfig.Pid())
	event.SetExtension(common.ProtocolKey.ClientInstanceKey.SYS, c.EventMeshHttpClientConfig.Sys())
	// FIXME Random string
	event.SetExtension(common.ProtocolKey.ClientInstanceKey.BIZSEQNO, "333333")
	event.SetExtension(common.ProtocolKey.ClientInstanceKey.UNIQUEID, "444444")
	event.SetExtension(common.ProtocolKey.LANGUAGE, gcommon.Constants.LANGUAGE_GO)
	// FIXME Java is name of spec version name
	//event.SetExtension(common.ProtocolKey.PROTOCOL_DESC, event.SpecVersion())
	event.SetExtension(common.ProtocolKey.PROTOCOL_VERSION, event.SpecVersion())

	return event
}
