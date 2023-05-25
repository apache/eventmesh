/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package consumer

import (
	"fmt"
	"github.com/apache/eventmesh/eventmesh-sdk-go/http/conf"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	. "github.com/onsi/ginkgo"
	. "github.com/onsi/gomega"
)

func TestConsumerAPIs(t *testing.T) {
	RegisterFailHandler(Fail)
	RunSpecs(t, "consumer module Tests")
}

var server *httptest.Server
var eventMeshHttpConsumer *EventMeshHttpConsumer

var _ = BeforeSuite(func() {
	f := func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
		w.Write([]byte(`{"retCode":0}`))
	}
	server = httptest.NewServer(http.HandlerFunc(f))

	eventMeshClientConfig := conf.DefaultEventMeshHttpClientConfig
	sp := strings.Split(server.URL, ":")
	eventMeshClientConfig.SetLiteEventMeshAddr(fmt.Sprintf("127.0.0.1:%s", sp[len(sp)-1]))
	eventMeshHttpConsumer = NewEventMeshHttpConsumer(eventMeshClientConfig)

})

var _ = AfterSuite(func() {
	if server != nil {
		server.Close()
	}

	if eventMeshHttpConsumer != nil {
		eventMeshHttpConsumer.Close()
	}
})
