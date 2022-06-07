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
	gtcp "github.com/apache/incubator-eventmesh/eventmesh-sdk-go/common/protocol/tcp"
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/tcp/conf"
)

type CloudEventTCPClient struct {
	cloudEventTCPPubClient *CloudEventTCPPubClient
	cloudEventTCPSubClient *CloudEventTCPSubClient
}

func NewCloudEventTCPClient(eventMeshTcpClientConfig conf.EventMeshTCPClientConfig) *CloudEventTCPClient {
	return &CloudEventTCPClient{
		cloudEventTCPPubClient: NewCloudEventTCPPubClient(eventMeshTcpClientConfig),
		cloudEventTCPSubClient: NewCloudEventTCPSubClient(eventMeshTcpClientConfig),
	}
}

func (c *CloudEventTCPClient) Init() {
	c.cloudEventTCPPubClient.init()
	c.cloudEventTCPSubClient.init()
}

func (c *CloudEventTCPClient) Publish(message interface{}, timeout int64) gtcp.Package {
	return c.cloudEventTCPPubClient.publish(message, timeout)
}

func (c *CloudEventTCPClient) GetPubClient() EventMeshTCPPubClient {
	return c.cloudEventTCPPubClient
}

func (c *CloudEventTCPClient) GetSubClient() EventMeshTCPSubClient {
	return c.cloudEventTCPSubClient
}
