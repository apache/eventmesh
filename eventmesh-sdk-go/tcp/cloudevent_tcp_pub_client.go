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
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/common/protocol/tcp"
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/tcp/conf"
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/tcp/utils"
)

type CloudEventTCPPubClient struct {
	*BaseTCPClient
}

func NewCloudEventTCPPubClient(eventMeshTcpClientConfig conf.EventMeshTCPClientConfig) *CloudEventTCPPubClient {
	return &CloudEventTCPPubClient{BaseTCPClient: NewBaseTCPClient(eventMeshTcpClientConfig)}
}

func (c CloudEventTCPPubClient) init() {
	c.Open()
	c.Hello()
	c.Heartbeat()
}

func (c CloudEventTCPPubClient) reconnect() {
	c.Reconnect()
	c.Heartbeat()
}

func (c CloudEventTCPPubClient) publish(message interface{}, timeout int64) tcp.Package {
	msg := utils.BuildPackage(message, tcp.DefaultCommand.ASYNC_MESSAGE_TO_SERVER)
	return c.IO(msg, timeout)
}
