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

package tcp

import (
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/common/protocol"
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/tcp/conf"
)

type CloudEventTCPSubClient struct {
	*BaseTCPClient
}

func NewCloudEventTCPSubClient(eventMeshTcpClientConfig conf.EventMeshTCPClientConfig) *CloudEventTCPSubClient {
	return &CloudEventTCPSubClient{BaseTCPClient: NewBaseTCPClient(eventMeshTcpClientConfig)}
}

func (c CloudEventTCPSubClient) init() {
	//panic("implement me")
}

func (c CloudEventTCPSubClient) subscribe(topic string, subscriptionMode protocol.SubscriptionMode, subscriptionType protocol.SubscriptionType) {
	panic("implement me")
}

func (c CloudEventTCPSubClient) unsubscribe() {
	panic("implement me")
}
