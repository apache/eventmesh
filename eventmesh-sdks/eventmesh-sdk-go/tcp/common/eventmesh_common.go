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

package common

var EventMeshCommon = struct {
	// Timeout time shared by the server
	DEFAULT_TIME_OUT_MILLS int

	// User agent
	USER_AGENT_PURPOSE_PUB string
	USER_AGENT_PURPOSE_SUB string

	// Protocol type
	CLOUD_EVENTS_PROTOCOL_NAME string
	EM_MESSAGE_PROTOCOL_NAME   string
	OPEN_MESSAGE_PROTOCOL_NAME string
}{
	DEFAULT_TIME_OUT_MILLS:     20 * 1000,
	USER_AGENT_PURPOSE_PUB:     "pub",
	USER_AGENT_PURPOSE_SUB:     "sub",
	CLOUD_EVENTS_PROTOCOL_NAME: "cloudevents",
	EM_MESSAGE_PROTOCOL_NAME:   "eventmeshmessage",
	OPEN_MESSAGE_PROTOCOL_NAME: "openmessage",
}
