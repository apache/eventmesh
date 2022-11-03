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

package producer

import (
	"fmt"
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/common"
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/common/utils"
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/http/conf"
	cloudevents "github.com/cloudevents/sdk-go/v2"
	"github.com/google/uuid"
	"github.com/stretchr/testify/assert"
	"net/http"
	"net/http/httptest"
	"strconv"
	"strings"
	"testing"
	"time"
)

func TestEventMeshHttpProducer_Publish(t *testing.T) {
	f := func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
		w.Write([]byte(`{"retCode":0}`))
	}
	server := httptest.NewServer(http.HandlerFunc(f))
	defer server.Close()

	eventMeshClientConfig := conf.DefaultEventMeshHttpClientConfig
	sp := strings.Split(server.URL, ":")
	eventMeshClientConfig.SetLiteEventMeshAddr(fmt.Sprintf("127.0.0.1:%s", sp[len(sp)-1]))
	// Make event to send
	event := cloudevents.NewEvent()
	event.SetID(uuid.New().String())
	event.SetSubject("test-topic")
	event.SetSource("test-uri")
	event.SetType(common.Constants.CLOUD_EVENTS_PROTOCOL_NAME)
	event.SetExtension(common.Constants.EVENTMESH_MESSAGE_CONST_TTL, strconv.Itoa(4*1000))
	event.SetDataContentType(cloudevents.ApplicationCloudEventsJSON)
	data := map[string]string{"hello": "EventMesh"}
	err := event.SetData(cloudevents.ApplicationCloudEventsJSON, utils.MarshalJsonBytes(data))
	if err != nil {
		t.Fail()
	}
	// Publish event
	httpProducer := NewEventMeshHttpProducer(eventMeshClientConfig)
	err = httpProducer.Publish(&event)
	assert.Nil(t, err)
}

func TestEventMeshHttpProducer_Request(t *testing.T) {
	f := func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
		w.Write([]byte(`{"retCode":0, "retMsg":"{\"topic\":\"test-topic\",\"body\":\"{\\\"data\\\":1}\",\"properties\":null}"}`))
	}

	server := httptest.NewServer(http.HandlerFunc(f))
	defer server.Close()

	eventMeshClientConfig := conf.DefaultEventMeshHttpClientConfig
	sp := strings.Split(server.URL, ":")
	eventMeshClientConfig.SetLiteEventMeshAddr(fmt.Sprintf("127.0.0.1:%s", sp[len(sp)-1]))
	// Make event to send
	event := cloudevents.NewEvent()
	event.SetID(uuid.New().String())
	event.SetSubject("test-topic")
	event.SetSource("test-uri")
	event.SetType(common.Constants.CLOUD_EVENTS_PROTOCOL_NAME)
	event.SetExtension(common.Constants.EVENTMESH_MESSAGE_CONST_TTL, strconv.Itoa(4*1000))
	event.SetDataContentType(cloudevents.ApplicationCloudEventsJSON)
	data := map[string]string{"hello": "EventMesh"}
	err := event.SetData(cloudevents.ApplicationCloudEventsJSON, utils.MarshalJsonBytes(data))
	if err != nil {
		t.Fail()
	}

	httpProducer := NewEventMeshHttpProducer(eventMeshClientConfig)
	ret, err := httpProducer.Request(&event, time.Second)
	assert.Nil(t, err)
	retData := make(map[string]interface{})
	utils.UnMarshalJsonString(string(ret.DataEncoded), &retData)
	assert.Equal(t, float64(1), retData["data"])
}
