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

package producer

import (
	gutils "github.com/apache/eventmesh/eventmesh-sdk-go/common/utils"
	ghttp "github.com/apache/eventmesh/eventmesh-sdk-go/http"
	. "github.com/onsi/ginkgo"
	. "github.com/onsi/gomega"
	"net/http"
	"net/http/httptest"
	"testing"
)

func TestProducerAPIs(t *testing.T) {
	RegisterFailHandler(Fail)
	RunSpecs(t, "producer module Tests")
}

var server *httptest.Server

var _ = BeforeSuite(func() {
	retData := make(map[string]interface{})
	retData["data"] = 1

	rpy := ghttp.ReplyMessage{
		Topic: "test-topic",
		Body:  gutils.MarshalJsonString(retData),
	}

	ret := ghttp.EventMeshRetObj{
		RetCode: 0,
		RetMsg:  gutils.MarshalJsonString(rpy),
	}
	f := func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
		w.Write([]byte(gutils.MarshalJsonString(ret)))
	}
	server = httptest.NewServer(http.HandlerFunc(f))
})

var _ = AfterSuite(func() {
	if server != nil {
		server.Close()
	}
})
