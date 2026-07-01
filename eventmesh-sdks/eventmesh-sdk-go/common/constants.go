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

var Constants = struct {
	LANGUAGE_GO                 string
	HTTP_PROTOCOL_PREFIX        string
	HTTPS_PROTOCOL_PREFIX       string
	PROTOCOL_TYPE               string
	PROTOCOL_VERSION            string
	PROTOCOL_DESC               string
	DEFAULT_HTTP_TIME_OUT       int64
	EVENTMESH_MESSAGE_CONST_TTL string

	// Client heartbeat interval
	HEARTBEAT int64

	// Protocol type
	CLOUD_EVENTS_PROTOCOL_NAME string
	EM_MESSAGE_PROTOCOL_NAME   string
	OPEN_MESSAGE_PROTOCOL_NAME string
}{
	LANGUAGE_GO:                 "GO",
	HTTP_PROTOCOL_PREFIX:        "http://",
	HTTPS_PROTOCOL_PREFIX:       "https://",
	PROTOCOL_TYPE:               "protocoltype",
	PROTOCOL_VERSION:            "protocolversion",
	PROTOCOL_DESC:               "protocoldesc",
	DEFAULT_HTTP_TIME_OUT:       15000,
	EVENTMESH_MESSAGE_CONST_TTL: "ttl",
	HEARTBEAT:                   30 * 1000,
	CLOUD_EVENTS_PROTOCOL_NAME:  "cloudevents",
	EM_MESSAGE_PROTOCOL_NAME:    "eventmeshmessage",
	OPEN_MESSAGE_PROTOCOL_NAME:  "openmessage",
}
