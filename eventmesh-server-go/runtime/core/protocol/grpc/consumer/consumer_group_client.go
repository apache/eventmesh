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
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/config"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/pkg/util"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/runtime/consts"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/runtime/core/protocol/grpc/emitter"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/runtime/proto/pb"
	"os"
	"time"
)

// GroupClient consumer group client details
type GroupClient struct {
	ENV              string
	IDC              string
	ConsumerGroup    string
	Topic            string
	GRPCType         consts.GRPCType
	URL              string
	SubscriptionMode pb.Subscription_SubscriptionItem_SubscriptionMode
	SYS              string
	IP               string
	PID              string
	Hostname         string
	APIVersion       string
	LastUPTime       time.Time
	Emiter           emitter.EventEmitter
}

func DefaultStreamGroupClient() *GroupClient {
	hostname, _ := os.Hostname()
	return &GroupClient{
		ENV:              config.GlobalConfig().Common.Env,
		IDC:              config.GlobalConfig().Common.IDC,
		ConsumerGroup:    "ConsumerGroup",
		Topic:            "Topic",
		GRPCType:         consts.STREAM,
		SubscriptionMode: pb.Subscription_SubscriptionItem_CLUSTERING,
		SYS:              "test-SYS",
		IP:               util.GetIP(),
		PID:              util.PID(),
		Hostname:         hostname,
		APIVersion:       "v1",
		URL:              "",
		LastUPTime:       time.Now(),
		Emiter:           emitter.NewEventEmitter(nil),
	}
}

func DefaultWebhookGroupClient() *GroupClient {
	hostname, _ := os.Hostname()
	return &GroupClient{
		ENV:              config.GlobalConfig().Common.Env,
		IDC:              config.GlobalConfig().Common.IDC,
		ConsumerGroup:    "ConsumerGroup",
		Topic:            "Topic",
		GRPCType:         consts.WEBHOOK,
		SubscriptionMode: pb.Subscription_SubscriptionItem_CLUSTERING,
		SYS:              "test-SYS",
		IP:               util.GetIP(),
		PID:              util.PID(),
		Hostname:         hostname,
		APIVersion:       "v1",
		URL:              "http://test.com",
		LastUPTime:       time.Now(),
		Emiter:           nil,
	}
}
