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
	"time"

	"github.com/google/uuid"
	"strconv"

	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/common"
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/common/protocol"
	gtcp "github.com/apache/incubator-eventmesh/eventmesh-sdk-go/common/protocol/tcp"
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/common/utils"
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/tcp"
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/tcp/conf"
	cloudevents "github.com/cloudevents/sdk-go/v2"
)

func AsyncPubCloudEvents() {
	eventMeshIp := "127.0.0.1"
	eventMeshTcpPort := 10000
	topic := "TEST-TOPIC-TCP-ASYNC"

	// Init client
	userAgent := gtcp.UserAgent{Env: "test", Subsystem: "5023", Path: "/data/app/umg_proxy", Pid: 32893,
		Host: "127.0.0.1", Port: 8362, Version: "2.0.11", Username: "PU4283", Password: "PUPASS", Idc: "FT",
		Group: "EventmeshTestGroup", Purpose: "pub"}
	config := conf.NewEventMeshTCPClientConfig(eventMeshIp, eventMeshTcpPort, userAgent)
	client := tcp.CreateEventMeshTCPClient(*config, protocol.DefaultMessageType.CloudEvent)
	client.Init()

	// Make event to send
	event := cloudevents.NewEvent()
	event.SetID(uuid.New().String())
	event.SetSubject(topic)
	event.SetSource("example/uri")
	event.SetType(common.Constants.CLOUD_EVENTS_PROTOCOL_NAME)
	event.SetExtension(common.Constants.EVENTMESH_MESSAGE_CONST_TTL, strconv.Itoa(4*1000))
	event.SetDataContentType(cloudevents.ApplicationCloudEventsJSON)
	data := map[string]string{"hello": "EventMesh"}
	event.SetData(cloudevents.ApplicationCloudEventsJSON, utils.MarshalJsonBytes(data))

	// Publish event
	client.Publish(event, 10000)
	time.Sleep(10 * time.Second)
}
