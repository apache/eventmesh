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
	"github.com/apache/eventmesh/eventmesh-sdk-go/common/protocol"
	. "github.com/onsi/ginkgo"
	. "github.com/onsi/gomega"
)

var _ = Describe("EventMeshHttpConsumer test", func() {

	Context("Subscribe test ", func() {
		It("should success", func() {

			subscribeUrl := "http://mock-url"
			topicList := []protocol.SubscriptionItem{
				{
					Topic: "mock-topic",
					Mode:  protocol.DefaultSubscriptionMode.CLUSTERING,
					Type:  protocol.DefaultSubscriptionType.ASYNC,
				},
			}
			eventMeshHttpConsumer.Subscribe(topicList, subscribeUrl)

			Ω(1).To(Equal(len(eventMeshHttpConsumer.subscriptions)))

			topicList = []protocol.SubscriptionItem{
				{
					Topic: "mock-topic2",
					Mode:  protocol.DefaultSubscriptionMode.CLUSTERING,
					Type:  protocol.DefaultSubscriptionType.ASYNC,
				},
			}
			eventMeshHttpConsumer.Subscribe(topicList, subscribeUrl)
			Ω(2).To(Equal(len(eventMeshHttpConsumer.subscriptions)))
			eventMeshHttpConsumer.Subscribe(topicList, subscribeUrl)
			Ω(2).To(Equal(len(eventMeshHttpConsumer.subscriptions)))
		})

	})

	Context("Unsubscribe test ", func() {
		It("should success", func() {
			subscribeUrl := "http://mock-url"
			var topicList = []string{"mock-topic"}
			eventMeshHttpConsumer.Unsubscribe(topicList, subscribeUrl)
			Ω(1).To(Equal(len(eventMeshHttpConsumer.subscriptions)))
			topicList = []string{"mock-topic2"}
			eventMeshHttpConsumer.Unsubscribe(topicList, subscribeUrl)
			Ω(0).To(Equal(len(eventMeshHttpConsumer.subscriptions)))
			topicList = []string{"mock-topic2"}
			eventMeshHttpConsumer.Unsubscribe(topicList, subscribeUrl)
			Ω(0).To(Equal(len(eventMeshHttpConsumer.subscriptions)))
		})

	})
})
