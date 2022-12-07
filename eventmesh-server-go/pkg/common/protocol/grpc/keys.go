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

package grpc

var (
	ENV              = "env"
	IDC              = "idc"
	SYS              = "sys"
	PID              = "pid"
	IP               = "ip"
	USERNAME         = "username"
	PASSWD           = "passwd"
	LANGUAGE         = "language"
	PROTOCOL_TYPE    = "protocoltype"
	PROTOCOL_VERSION = "protocolversion"
	PROTOCOL_DESC    = "protocoldesc"
	SEQ_NUM          = "seqnum"
	UNIQUE_ID        = "uniqueid"
	TTL              = "ttl"
	PRODUCERGROUP    = "producergroup"
	TAG              = "tag"
	CONTENT_TYPE     = "contenttype"
)

// request code
var (
	HTTP_PUSH_CLIENT_ASYNC = "105"
)

// protocol key
var (
	REQUEST_CODE = "code"
	Version      = "version"
)

// EventMeshInstanceKey
var (
	EVENTMESHCLUSTER = "eventmeshcluster"
	EVENTMESHIP      = "eventmeship"
	EVENTMESHENV     = "eventmeshenv"
	EVENTMESHIDC     = "eventmeshidc"
)

// PushMessageRequestBody
var (
	RANDOMNO  = "randomNo"
	TOPIC     = "topic"
	BIZSEQNO  = "bizseqno"
	UNIQUEID  = "uniqueId"
	CONTENT   = "content"
	EXTFIELDS = "extFields"
)
