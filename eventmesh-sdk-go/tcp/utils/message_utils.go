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

package utils

import (
	gcommon "github.com/apache/incubator-eventmesh/eventmesh-sdk-go/common"
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/common/protocol/tcp"
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/log"
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/tcp/common"
	cloudevents "github.com/cloudevents/sdk-go/v2"
)

func BuildPackage(message interface{}, command tcp.Command) tcp.Package {
	// FIXME Support random sequence
	header := tcp.NewHeader(command, 0, "", "22222")
	pkg := tcp.NewPackage(header)

	if _, ok := message.(cloudevents.Event); ok {
		event := message.(cloudevents.Event)
		eventBytes, err := event.MarshalJSON()
		if err != nil {
			log.Fatalf("Failed to marshal cloud event")
		}

		pkg.Header.PutProperty(gcommon.Constants.PROTOCOL_TYPE, common.EventMeshCommon.CLOUD_EVENTS_PROTOCOL_NAME)
		pkg.Header.PutProperty(gcommon.Constants.PROTOCOL_VERSION, event.SpecVersion())
		pkg.Header.PutProperty(gcommon.Constants.PROTOCOL_DESC, "tcp")
		pkg.Body = eventBytes
	}

	return pkg
}

func BuildHelloPackage(agent tcp.UserAgent) tcp.Package {
	// FIXME Support random sequence
	header := tcp.NewHeader(tcp.DefaultCommand.HELLO_REQUEST, 0, "", "22222")
	msg := tcp.NewPackage(header)
	msg.Body = agent
	return msg
}

func BuildHeartBeatPackage() tcp.Package {
	// FIXME Support random sequence
	header := tcp.NewHeader(tcp.DefaultCommand.HEARTBEAT_REQUEST, 0, "", "22222")
	msg := tcp.NewPackage(header)
	return msg
}
