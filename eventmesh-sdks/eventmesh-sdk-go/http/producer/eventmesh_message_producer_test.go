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
	"fmt"
	"github.com/apache/eventmesh/eventmesh-sdk-go/common/protocol"
	"github.com/apache/eventmesh/eventmesh-sdk-go/common/utils"
	"github.com/apache/eventmesh/eventmesh-sdk-go/http/conf"
	. "github.com/onsi/ginkgo"
	. "github.com/onsi/gomega"
	"strings"
	"time"
)

var _ = Describe("EventMeshHttpProducer test", func() {

	Context("PublishEventMeshMessage  test", func() {
		It("should success", func() {
			eventMeshClientConfig := conf.DefaultEventMeshHttpClientConfig
			sp := strings.Split(server.URL, ":")
			eventMeshClientConfig.SetLiteEventMeshAddr(fmt.Sprintf("127.0.0.1:%s", sp[len(sp)-1]))

			message := &protocol.EventMeshMessage{
				BizSeqNo: "test-biz-no",
				UniqueId: "test-unique-id",
				Topic:    "test-topic",
				Content:  "test-content",
				Prop:     map[string]string{"hello": "EventMesh"},
			}
			// Publish event
			httpProducer := NewEventMeshHttpProducer(eventMeshClientConfig)
			err := httpProducer.PublishEventMeshMessage(message)
			Ω(err).NotTo(HaveOccurred())
		})
	})

	Context("RequestEventMeshMessage  test", func() {
		It("should success", func() {
			eventMeshClientConfig := conf.DefaultEventMeshHttpClientConfig
			sp := strings.Split(server.URL, ":")
			eventMeshClientConfig.SetLiteEventMeshAddr(fmt.Sprintf("127.0.0.1:%s", sp[len(sp)-1]))

			message := &protocol.EventMeshMessage{
				BizSeqNo: "test-biz-no",
				UniqueId: "test-unique-id",
				Topic:    "test-topic",
				Content:  "test-content",
				Prop:     map[string]string{"hello": "EventMesh"},
			}
			httpProducer := NewEventMeshHttpProducer(eventMeshClientConfig)
			ret, err := httpProducer.RequestEventMeshMessage(message, time.Second)
			Ω(err).NotTo(HaveOccurred())

			retData := make(map[string]interface{})
			utils.UnMarshalJsonString(ret.Content, &retData)
			Ω(float64(1)).To(Equal(retData["data"]))
		})
	})
})
