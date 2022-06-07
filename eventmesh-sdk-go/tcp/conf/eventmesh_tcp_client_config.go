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

package conf

import "github.com/apache/incubator-eventmesh/eventmesh-sdk-go/common/protocol/tcp"

type EventMeshTCPClientConfig struct {
	host      string
	port      int
	userAgent tcp.UserAgent
}

func NewEventMeshTCPClientConfig(host string, port int, userAgent tcp.UserAgent) *EventMeshTCPClientConfig {
	return &EventMeshTCPClientConfig{host: host, port: port, userAgent: userAgent}
}

func (e *EventMeshTCPClientConfig) Host() string {
	return e.host
}

func (e *EventMeshTCPClientConfig) SetHost(host string) {
	e.host = host
}

func (e *EventMeshTCPClientConfig) Port() int {
	return e.port
}

func (e *EventMeshTCPClientConfig) SetPort(port int) {
	e.port = port
}

func (e *EventMeshTCPClientConfig) UserAgent() tcp.UserAgent {
	return e.userAgent
}

func (e *EventMeshTCPClientConfig) SetUserAgent(userAgent tcp.UserAgent) {
	e.userAgent = userAgent
}
