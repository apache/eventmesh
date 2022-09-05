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

package config

import (
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/runtime/proto/pb"
	"github.com/liyue201/gostl/ds/set"
	"sync"
)

type ConsumerGroupConfig struct {
	ConsumerGroup string

	// key is topic, value is  ConsumerGroupTopicConfig
	ConsumerGroupTopicConfigs sync.Map
}

type GRPCType string

const (
	WEBHOOK GRPCType = "WEBHOOK"
	STREAM  GRPCType = "STREAM"
)

type StateAction string

const (
	NEW    StateAction = "NEW"
	CHANGE StateAction = "CHANGE"
	DELETE StateAction = "DELETE"
)

type ConsumerGroupTopicConfig struct {
	ConsumerGroup    string
	Topic            string
	SubscriptionMode pb.Subscription_SubscriptionItem_SubscriptionMode
	GRPCType         GRPCType
	// IDCWebhookURLs webhook urls seperated by IDC
	// key is IDC, value is vector.Vector
	IDCWebhookURLs *sync.Map

	// AllURLs all webhook urls, ignore idc
	AllURLs *set.Set
}

type ConsumerGroupMetadata struct {
	ConsumerGroup              string
	ConsumerGroupTopicMetadata *sync.Map
}

type ConsumerGroupTopicMetadata struct {
	ConsumerGroup string
	Topic         string
	AllURLs       *set.Set
}

type ConsumerGroupStateEvent struct {
	ConsumerGroup            string
	ConsumerGroupConfig      *ConsumerGroupConfig
	ConsumerGroupStateAction StateAction
}

type ConsumerGroupTopicConfChangeEvent struct {
	Action                   StateAction
	ConsumerGroup            string
	ConsumerGroupTopicConfig *ConsumerGroupTopicConfig
}
