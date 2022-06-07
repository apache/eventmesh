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

package client

import (
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/common/protocol/http/body"
)

var HeartbeatRequestBodyKey = struct {
	CLIENTTYPE        string
	CONSUMERGROUP     string
	HEARTBEATENTITIES string
}{
	CLIENTTYPE:        "clientType",
	HEARTBEATENTITIES: "heartbeatEntities",
	CONSUMERGROUP:     "consumerGroup",
}

type HeartbeatEntity struct {
	Topic      string `json:"topic"`
	Url        string `json:"url"`
	ServiceId  string `json:"serviceId"`
	InstanceId string `json:"instanceId"`
}

type HeartbeatRequestBody struct {
	body.Body
	consumerGroup     string
	clientType        string
	heartbeatEntities string
}

func (h *HeartbeatRequestBody) ConsumerGroup() string {
	return h.consumerGroup
}

func (h *HeartbeatRequestBody) SetConsumerGroup(consumerGroup string) {
	h.consumerGroup = consumerGroup
}

func (h *HeartbeatRequestBody) ClientType() string {
	return h.clientType
}

func (h *HeartbeatRequestBody) SetClientType(clientType string) {
	h.clientType = clientType
}

func (h *HeartbeatRequestBody) HeartbeatEntities() string {
	return h.heartbeatEntities
}

func (h *HeartbeatRequestBody) SetHeartbeatEntities(heartbeatEntities string) {
	h.heartbeatEntities = heartbeatEntities
}

func (h *HeartbeatRequestBody) BuildBody(bodyParam map[string]interface{}) *HeartbeatRequestBody {
	return nil
}
