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
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/runtime/consts"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/runtime/proto/pb"
	"github.com/stretchr/testify/assert"
	"testing"
)

func Test_ConsumerGroupTopicOption(t *testing.T) {
	tests := []struct {
		name          string
		consumerGroup string
		topic         string
		mode          pb.Subscription_SubscriptionItem_SubscriptionMode
		grpcType      consts.GRPCType
		expect        func(t *testing.T, option ConsumerGroupTopicOption)
	}{
		{
			name:          "create grpc stream broadcasting option",
			consumerGroup: "consumergroup",
			topic:         "topic",
			mode:          pb.Subscription_SubscriptionItem_BROADCASTING,
			grpcType:      consts.STREAM,
			expect: func(t *testing.T, option ConsumerGroupTopicOption) {
				assert.NotNil(t, option)
				assert.Equal(t, option.Topic(), "topic")
				assert.Equal(t, option.GRPCType(), consts.STREAM)
				assert.Equal(t, option.ConsumerGroup(), "consumergroup")
				assert.Equal(t, option.SubscriptionMode(), pb.Subscription_SubscriptionItem_BROADCASTING)
				assert.NotNil(t, option.RegisterClient())
				assert.NotNil(t, option.DeregisterClient())
				assert.NotNil(t, option.IDCEmiters())
				assert.NotNil(t, option.AllEmiters())
			},
		},
		{
			name:          "create grpc stream clustering option",
			consumerGroup: "consumergroup",
			topic:         "topic",
			mode:          pb.Subscription_SubscriptionItem_CLUSTERING,
			grpcType:      consts.STREAM,
			expect: func(t *testing.T, option ConsumerGroupTopicOption) {
				assert.NotNil(t, option)
				assert.Equal(t, option.Topic(), "topic")
				assert.Equal(t, option.GRPCType(), consts.STREAM)
				assert.Equal(t, option.ConsumerGroup(), "consumergroup")
				assert.Equal(t, option.SubscriptionMode(), pb.Subscription_SubscriptionItem_CLUSTERING)
				assert.NotNil(t, option.RegisterClient())
				assert.NotNil(t, option.DeregisterClient())
				assert.NotNil(t, option.IDCEmiters())
				assert.NotNil(t, option.AllEmiters())
			},
		},
		{
			name:          "create webhook clustering option",
			consumerGroup: "consumergroup",
			topic:         "topic",
			mode:          pb.Subscription_SubscriptionItem_CLUSTERING,
			grpcType:      consts.WEBHOOK,
			expect: func(t *testing.T, option ConsumerGroupTopicOption) {
				assert.NotNil(t, option)
				assert.Equal(t, option.Topic(), "topic")
				assert.Equal(t, option.GRPCType(), consts.WEBHOOK)
				assert.Equal(t, option.ConsumerGroup(), "consumergroup")
				assert.Equal(t, option.SubscriptionMode(), pb.Subscription_SubscriptionItem_CLUSTERING)
				assert.NotNil(t, option.RegisterClient())
				assert.NotNil(t, option.DeregisterClient())
				assert.NotNil(t, option.IDCURLs())
				assert.NotNil(t, option.AllURLs())
				assert.Equal(t, option.Size(), 0)
			},
		},
		{
			name:          "create webhook broadcasting option",
			consumerGroup: "consumergroup",
			topic:         "topic",
			mode:          pb.Subscription_SubscriptionItem_BROADCASTING,
			grpcType:      consts.WEBHOOK,
			expect: func(t *testing.T, option ConsumerGroupTopicOption) {
				assert.NotNil(t, option)
				assert.Equal(t, option.Topic(), "topic")
				assert.Equal(t, option.GRPCType(), consts.WEBHOOK)
				assert.Equal(t, option.ConsumerGroup(), "consumergroup")
				assert.Equal(t, option.SubscriptionMode(), pb.Subscription_SubscriptionItem_BROADCASTING)
				assert.NotNil(t, option.RegisterClient())
				assert.NotNil(t, option.DeregisterClient())
				assert.NotNil(t, option.IDCURLs())
				assert.NotNil(t, option.AllURLs())
				assert.Equal(t, option.Size(), 0)
			},
		},
	}

	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			option := NewConsumerGroupTopicOption(tc.consumerGroup, tc.topic, tc.mode, tc.grpcType)
			tc.expect(t, option)
		})
	}
}
