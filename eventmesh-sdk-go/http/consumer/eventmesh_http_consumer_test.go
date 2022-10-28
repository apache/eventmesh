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

package consumer

import (
	"fmt"
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/common/protocol"
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/http/conf"
	"github.com/stretchr/testify/assert"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
)

func TestEventMeshHttpConsumer_Subscribe(t *testing.T) {
	f := func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
		w.Write([]byte(`{"retCode":0}`))
	}
	server := httptest.NewServer(http.HandlerFunc(f))
	defer server.Close()

	eventMeshClientConfig := conf.DefaultEventMeshHttpClientConfig
	sp := strings.Split(server.URL, ":")
	eventMeshClientConfig.SetLiteEventMeshAddr(fmt.Sprintf("127.0.0.1:%s", sp[len(sp)-1]))
	eventMeshHttpConsumer := NewEventMeshHttpConsumer(eventMeshClientConfig)

	subscribeUrl := "http://mock-url"
	topicList := []protocol.SubscriptionItem{
		{
			Topic: "mock-topic",
			Mode:  protocol.DefaultSubscriptionMode.CLUSTERING,
			Type:  protocol.DefaultSubscriptionType.ASYNC,
		},
	}
	eventMeshHttpConsumer.Subscribe(topicList, subscribeUrl)
	assert.Equal(t, 1, len(eventMeshHttpConsumer.subscriptions))

	topicList = []protocol.SubscriptionItem{
		{
			Topic: "mock-topic2",
			Mode:  protocol.DefaultSubscriptionMode.CLUSTERING,
			Type:  protocol.DefaultSubscriptionType.ASYNC,
		},
	}
	eventMeshHttpConsumer.Subscribe(topicList, subscribeUrl)
	assert.Equal(t, 2, len(eventMeshHttpConsumer.subscriptions))
}

func TestEventMeshHttpConsumer_Unsubscribe(t *testing.T) {
	f := func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
		w.Write([]byte(`{"retCode":0}`))
	}
	server := httptest.NewServer(http.HandlerFunc(f))
	defer server.Close()

	eventMeshClientConfig := conf.DefaultEventMeshHttpClientConfig
	sp := strings.Split(server.URL, ":")
	eventMeshClientConfig.SetLiteEventMeshAddr(fmt.Sprintf("127.0.0.1:%s", sp[len(sp)-1]))
	eventMeshHttpConsumer := NewEventMeshHttpConsumer(eventMeshClientConfig)

	subscribeUrl := "http://mock-url"
	subscribeList := []protocol.SubscriptionItem{
		{
			Topic: "mock-topic",
			Mode:  protocol.DefaultSubscriptionMode.CLUSTERING,
			Type:  protocol.DefaultSubscriptionType.ASYNC,
		},
	}
	eventMeshHttpConsumer.Subscribe(subscribeList, subscribeUrl)
	assert.Equal(t, 1, len(eventMeshHttpConsumer.subscriptions))

	var topicList = []string{"mock-topic"}
	eventMeshHttpConsumer.Unsubscribe(topicList, subscribeUrl)
	assert.Equal(t, 0, len(eventMeshHttpConsumer.subscriptions))
}
