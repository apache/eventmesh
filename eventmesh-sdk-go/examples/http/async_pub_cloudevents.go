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
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/common"
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/common/utils"
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/http/conf"
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/http/producer"
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/log"

	cloudevents "github.com/cloudevents/sdk-go/v2"
	"github.com/google/uuid"

	"os"
	"strconv"
)

func AsyncPubCloudEvents() {
	eventMeshIPPort := "127.0.0.1" + ":" + "10105"
	producerGroup := "EventMeshTest-producerGroup"
	topic := "TEST-TOPIC-HTTP-ASYNC"
	env := "P"
	idc := "FT"
	subSys := "1234"
	// FIXME Get ip dynamically
	localIp := "127.0.0.1"

	// (Deep) Copy of default config
	eventMeshClientConfig := conf.DefaultEventMeshHttpClientConfig
	eventMeshClientConfig.SetLiteEventMeshAddr(eventMeshIPPort)
	eventMeshClientConfig.SetProducerGroup(producerGroup)
	eventMeshClientConfig.SetEnv(env)
	eventMeshClientConfig.SetIdc(idc)
	eventMeshClientConfig.SetSys(subSys)
	eventMeshClientConfig.SetIp(localIp)
	eventMeshClientConfig.SetPid(strconv.Itoa(os.Getpid()))

	// Make event to send
	event := cloudevents.NewEvent()
	event.SetID(uuid.New().String())
	event.SetSubject(topic)
	event.SetSource("example/uri")
	event.SetType(common.Constants.CLOUD_EVENTS_PROTOCOL_NAME)
	event.SetExtension(common.Constants.EVENTMESH_MESSAGE_CONST_TTL, strconv.Itoa(4*1000))
	event.SetDataContentType(cloudevents.ApplicationCloudEventsJSON)
	data := map[string]string{"hello": "EventMesh"}
	err := event.SetData(cloudevents.ApplicationCloudEventsJSON, utils.MarshalJsonBytes(data))
	if err != nil {
		log.Fatalf("Failed to set cloud event data, error: %v", err)
	}

	// Publish event
	httpProducer := producer.NewEventMeshHttpProducer(eventMeshClientConfig)
	httpProducer.Publish(&event)
}
