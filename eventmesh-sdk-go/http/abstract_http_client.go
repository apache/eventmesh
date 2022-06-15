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

package http

import (
	gcommon "github.com/apache/incubator-eventmesh/eventmesh-sdk-go/common"
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/http/conf"
	nethttp "net/http"
	"time"
)

type AbstractHttpClient struct {
	EventMeshHttpClientConfig conf.EventMeshHttpClientConfig
	HttpClient                *nethttp.Client
}

func NewAbstractHttpClient(eventMeshHttpClientConfig conf.EventMeshHttpClientConfig) *AbstractHttpClient {
	c := &AbstractHttpClient{EventMeshHttpClientConfig: eventMeshHttpClientConfig}
	c.HttpClient = c.SetHttpClient()
	return c
}

func (c *AbstractHttpClient) Close() {
	//	Http Client does not need to close explicitly
}

func (c *AbstractHttpClient) SetHttpClient() *nethttp.Client {
	if !c.EventMeshHttpClientConfig.UseTls() {
		return &nethttp.Client{Timeout: 100 * time.Second}
	}

	// Use TLS
	return &nethttp.Client{Timeout: 100 * time.Second}
}

func (c *AbstractHttpClient) SelectEventMesh() string {
	// FIXME Add load balance support
	uri := c.EventMeshHttpClientConfig.LiteEventMeshAddr()

	if c.EventMeshHttpClientConfig.UseTls() {
		return gcommon.Constants.HTTPS_PROTOCOL_PREFIX + uri
	}

	return gcommon.Constants.HTTP_PROTOCOL_PREFIX + uri
}
