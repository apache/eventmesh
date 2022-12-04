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

package grpc

import (
	"context"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"

	"github.com/apache/incubator-eventmesh/eventmesh-server-go/pkg/util"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/runtime/proto/pb"
)

//	{
//	   "header": {
//	       "env": "11",
//	       "region": "sh",
//	       "idc": "test-idc",
//	       "ip": "169.254.45.15",
//	       "pid": "12345",
//	       "sys": "test",
//	       "username": "username",
//	       "password": "password",
//	       "language": "go",
//	       "protocolType": "cloudevents",
//	       "protocolVersion": "1.0",
//	       "protocolDesc": "nil"
//	   },
//	   "subscriptionItems": [
//	       {
//	           "topic":"grpc-topic",
//	           "mode":"CLUSTERING",
//	           "type":"SYNC"
//	       }
//	   ],
//	   "consumerGroup": "test-grpc-group",
//	   "url": "http://127.0.0.1:18080"
//	}
func Test_Subscribe(t *testing.T) {
	cli := newTestClient(t)
	assert.NotNil(t, cli)
	resp, err := cli.consumerClient.Subscribe(context.TODO(), &pb.Subscription{
		Header: &pb.RequestHeader{
			Env:             "grpc-env",
			Region:          "sh",
			Idc:             "idc-sh",
			Ip:              util.GetIP(),
			Pid:             util.PID(),
			Sys:             "grpc-sys",
			Username:        "grpc-username",
			Password:        "grpc-passwd",
			Language:        "Go",
			ProtocolType:    "cloudevents",
			ProtocolVersion: "1.0",
			ProtocolDesc:    "cloudevents",
		},
		ConsumerGroup: "grpc-stream-consumergroup",
		SubscriptionItems: []*pb.Subscription_SubscriptionItem{
			{
				Topic: _testWebhookTopic,
				Mode:  pb.Subscription_SubscriptionItem_CLUSTERING,
				Type:  pb.Subscription_SubscriptionItem_SYNC,
			},
		},
		Url: "http://127.0.0.1:18080/onmessage",
	})
	assert.NoError(t, err)
	assert.NotNil(t, cli)
	t.Log(resp.String())
	assert.Equal(t, resp.RespCode, 0)
}

func Test_unsubscribe(t *testing.T) {
	cli := newTestClient(t)
	assert.NotNil(t, cli)
	resp, err := cli.consumerClient.Unsubscribe(context.TODO(), &pb.Subscription{
		Header: &pb.RequestHeader{
			Env:             "grpc-env",
			Region:          "sh",
			Idc:             "idc-sh",
			Ip:              util.GetIP(),
			Pid:             util.PID(),
			Sys:             "grpc-sys",
			Username:        "grpc-username",
			Password:        "grpc-passwd",
			Language:        "Go",
			ProtocolType:    "cloudevents",
			ProtocolVersion: "1.0",
			ProtocolDesc:    "cloudevents",
		},
		ConsumerGroup: "grpc-stream-consumergroup",
		SubscriptionItems: []*pb.Subscription_SubscriptionItem{
			{
				Topic: _testWebhookTopic,
				Mode:  pb.Subscription_SubscriptionItem_CLUSTERING,
				Type:  pb.Subscription_SubscriptionItem_SYNC,
			},
		},
		Url: "http://127.0.0.1:18080/onmessage",
	})
	assert.NoError(t, err)
	assert.NotNil(t, cli)
	t.Log(resp.String())
	assert.Equal(t, resp.RespCode, 0)
}

func Test_subscribeStream(t *testing.T) {
	cli := newTestClient(t)
	assert.NotNil(t, cli)

	stream, err := cli.consumerClient.SubscribeStream(context.TODO())
	assert.NoError(t, err)
	err = stream.Send(&pb.Subscription{
		Header: &pb.RequestHeader{
			Env:             "grpc-env",
			Region:          "sh",
			Idc:             "idc-sh",
			Ip:              util.GetIP(),
			Pid:             util.PID(),
			Sys:             "grpc-sys",
			Username:        "grpc-username",
			Password:        "grpc-passwd",
			Language:        "Go",
			ProtocolType:    "cloudevents",
			ProtocolVersion: "1.0",
			ProtocolDesc:    "cloudevents",
		},
		ConsumerGroup: "grpc-stream-consumergroup",
		SubscriptionItems: []*pb.Subscription_SubscriptionItem{
			{
				Topic: _testStreamTopic,
				Mode:  pb.Subscription_SubscriptionItem_CLUSTERING,
				Type:  pb.Subscription_SubscriptionItem_ASYNC,
			},
		},
	})
	assert.NoError(t, err)
	time.Sleep(time.Hour)
}
