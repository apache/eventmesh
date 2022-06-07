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

type ClientInstanceKey struct {
	//Protocol layer requester description
	ENV      string
	IDC      string
	SYS      string
	PID      string
	IP       string
	USERNAME string
	PASSWORD string
	BIZSEQNO string
	UNIQUEID string
}

type EventMeshInstanceKey struct {
	//Protocol layer EventMesh description
	EVENTMESHCLUSTER string
	EVENTMESHIP      string
	EVENTMESHENV     string
	EVENTMESHIDC     string
}

var ProtocolKey = struct {
	REQUEST_CODE     string
	LANGUAGE         string
	VERSION          string
	PROTOCOL_TYPE    string
	PROTOCOL_VERSION string
	PROTOCOL_DESC    string

	ClientInstanceKey ClientInstanceKey

	EventMeshInstanceKey EventMeshInstanceKey

	//return of CLIENT <-> EventMesh
	RETCODE string
	RETMSG  string
	RESTIME string
}{
	REQUEST_CODE:     "code",
	LANGUAGE:         "language",
	VERSION:          "version",
	PROTOCOL_TYPE:    "protocoltype",
	PROTOCOL_VERSION: "protocolversion",
	PROTOCOL_DESC:    "protocoldesc",

	ClientInstanceKey: ClientInstanceKey{
		ENV:      "env",
		IDC:      "idc",
		SYS:      "sys",
		PID:      "pid",
		IP:       "ip",
		USERNAME: "username",
		PASSWORD: "passwd",
		BIZSEQNO: "bizseqno",
		UNIQUEID: "uniqueid",
	},

	EventMeshInstanceKey: EventMeshInstanceKey{
		EVENTMESHCLUSTER: "eventmeshcluster",
		EVENTMESHIP:      "eventmeship",
		EVENTMESHENV:     "eventmeshenv",
		EVENTMESHIDC:     "eventmeshidc",
	},

	RETCODE: "retCode",
	RETMSG:  "retMsg",
	RESTIME: "resTime",
}
