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
	"github.com/apache/eventmesh/eventmesh-sdk-go/common"
	"github.com/apache/eventmesh/eventmesh-sdk-go/common/utils"
	"github.com/apache/eventmesh/eventmesh-sdk-go/http/conf"
	cloudevents "github.com/cloudevents/sdk-go/v2"
	"github.com/google/uuid"
	. "github.com/onsi/ginkgo"
	. "github.com/onsi/gomega"
	"strconv"
	"strings"
	"time"
)

var _ = Describe("EventMeshHttpProducer test", func() {

	Context("PublishCloudEvent  test", func() {
		It("should success", func() {
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
			Ω(err).NotTo(HaveOccurred())

			// Publish event
			httpProducer := NewEventMeshHttpProducer(eventMeshClientConfig)
			err = httpProducer.PublishCloudEvent(&event)
			Ω(err).NotTo(HaveOccurred())
		})
	})

	Context("RequestCloudEvent  test", func() {
		It("should success", func() {
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
			Ω(err).NotTo(HaveOccurred())

			httpProducer := NewEventMeshHttpProducer(eventMeshClientConfig)
			ret, err := httpProducer.RequestCloudEvent(&event, time.Second)
			Ω(err).NotTo(HaveOccurred())

			retData := make(map[string]interface{})
			utils.UnMarshalJsonString(string(ret.DataEncoded), &retData)
			Ω(float64(1)).To(Equal(retData["data"]))
		})
	})
})
