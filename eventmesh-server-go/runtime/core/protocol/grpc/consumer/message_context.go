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
	cloudv2 "github.com/cloudevents/sdk-go/v2"
)

type MessageContext struct {
	MsgRandomNo      string
	SubscriptionMode pb.Subscription_SubscriptionItem_SubscriptionMode
	GrpcType         consts.GRPCType
	ConsumerGroup    string
	Event            *cloudv2.Event
	TopicConfig      ConsumerGroupTopicOption
}
