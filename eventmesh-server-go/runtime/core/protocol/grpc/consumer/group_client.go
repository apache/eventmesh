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
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/runtime/proto/pb"
	"time"
)

type GRPCType string

const (
	WEBHOOK GRPCType = "WEBHOOK"
	STREAM  GRPCType = "STREAM"
)

// GroupClient consumer group client details
type GroupClient struct {
	ENV              string
	IDC              string
	ConsumerGroup    string
	Topic            string
	GRPCType         GRPCType
	URL              string
	SubscriptionMode pb.Subscription_SubscriptionItem_SubscriptionMode
	SYS              string
	IP               string
	PID              string
	Hostname         string
	APIVersion       string
	LastUPtime       time.Time
}
