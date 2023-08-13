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
	gcommon "github.com/apache/eventmesh/eventmesh-sdk-go/common"
	"github.com/apache/eventmesh/eventmesh-sdk-go/common/protocol"
	"github.com/apache/eventmesh/eventmesh-sdk-go/common/protocol/http/common"
	protocol_message "github.com/apache/eventmesh/eventmesh-sdk-go/common/protocol/http/message"
	gutils "github.com/apache/eventmesh/eventmesh-sdk-go/common/utils"
	"github.com/apache/eventmesh/eventmesh-sdk-go/http"
	"github.com/apache/eventmesh/eventmesh-sdk-go/http/conf"
	"github.com/apache/eventmesh/eventmesh-sdk-go/http/constants"
	"github.com/apache/eventmesh/eventmesh-sdk-go/http/model"
	"github.com/apache/eventmesh/eventmesh-sdk-go/http/utils"
	nethttp "net/http"
	"strconv"
	"time"
)

type EventMeshMessageProducer struct {
	*http.AbstractHttpClient
}

func NewEventMeshMessageProducer(eventMeshHttpClientConfig conf.EventMeshHttpClientConfig) *EventMeshMessageProducer {
	c := &EventMeshMessageProducer{AbstractHttpClient: http.NewAbstractHttpClient(eventMeshHttpClientConfig)}
	return c
}

func (c *EventMeshMessageProducer) Publish(message *protocol.EventMeshMessage) error {
	err := c.validateEventMeshMessage(message)
	if err != nil {
		return err
	}

	requestParam := c.buildCommonPostParam(message)
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

func (c *EventMeshMessageProducer) Request(message *protocol.EventMeshMessage, timeout time.Duration) (*protocol.EventMeshMessage, error) {
	err := c.validateEventMeshMessage(message)
	if err != nil {
		return nil, err
	}

	requestParam := c.buildCommonPostParam(message)
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

func (c *EventMeshMessageProducer) transferMessage(retObj *http.EventMeshRetObj) (message *protocol.EventMeshMessage, err error) {
	defer func() {
		if r := recover(); r != nil {
			err = errors.New("fail to transfer from EventMeshRetObj to EventMeshMessage")
		}
	}()
	var replyMessage http.ReplyMessage
	gutils.UnMarshalJsonString(retObj.RetMsg, &replyMessage)
	return &protocol.EventMeshMessage{
		Topic:   replyMessage.Topic,
		Content: replyMessage.Body,
		Prop:    replyMessage.Properties,
	}, nil
}

func (c *EventMeshMessageProducer) buildCommonPostParam(message *protocol.EventMeshMessage) *model.RequestParam {
	requestParam := model.NewRequestParam(nethttp.MethodPost)
	requestParam.AddHeader(common.ProtocolKey.ClientInstanceKey.ENV, c.EventMeshHttpClientConfig.Env())
	requestParam.AddHeader(common.ProtocolKey.ClientInstanceKey.IDC, c.EventMeshHttpClientConfig.Idc())
	requestParam.AddHeader(common.ProtocolKey.ClientInstanceKey.IP, c.EventMeshHttpClientConfig.Ip())
	requestParam.AddHeader(common.ProtocolKey.ClientInstanceKey.PID, c.EventMeshHttpClientConfig.Pid())
	requestParam.AddHeader(common.ProtocolKey.ClientInstanceKey.SYS, c.EventMeshHttpClientConfig.Sys())
	requestParam.AddHeader(common.ProtocolKey.ClientInstanceKey.USERNAME, c.EventMeshHttpClientConfig.UserName())
	requestParam.AddHeader(common.ProtocolKey.ClientInstanceKey.PASSWORD, c.EventMeshHttpClientConfig.Password())
	requestParam.AddHeader(common.ProtocolKey.VERSION, common.DefaultProtocolVersion.V1.Version())
	requestParam.AddHeader(common.ProtocolKey.PROTOCOL_TYPE, constants.EventMeshMessageProtocol)
	requestParam.AddHeader(common.ProtocolKey.PROTOCOL_DESC, constants.ProtocolDesc)
	requestParam.AddHeader(common.ProtocolKey.PROTOCOL_VERSION, common.DefaultProtocolVersion.V1.Version())
	requestParam.AddHeader(common.ProtocolKey.LANGUAGE, gcommon.Constants.LANGUAGE_GO)

	requestParam.AddBody(protocol_message.SendMessageRequestBodyKey.PRODUCERGROUP, c.EventMeshHttpClientConfig.ProducerGroup())
	requestParam.AddBody(protocol_message.SendMessageRequestBodyKey.TOPIC, message.Topic)
	requestParam.AddBody(protocol_message.SendMessageRequestBodyKey.CONTENT, message.Content)
	requestParam.AddBody(protocol_message.SendMessageRequestBodyKey.TTL, message.Prop[constants.EventMeshMessageConstTTL])
	requestParam.AddBody(protocol_message.SendMessageRequestBodyKey.BIZSEQNO, message.BizSeqNo)
	requestParam.AddBody(protocol_message.SendMessageRequestBodyKey.UNIQUEID, message.UniqueId)
	return requestParam
}

func (c *EventMeshMessageProducer) validateEventMeshMessage(message *protocol.EventMeshMessage) error {
	if message == nil {
		return errors.New("EventMeshMessage can not be nil")
	}
	if len(message.Topic) == 0 {
		return errors.New("EventMeshMessage topic can not be empty")
	}
	if len(message.Content) == 0 {
		return errors.New("EventMeshMessage content can not be empty")
	}
	return nil
}
