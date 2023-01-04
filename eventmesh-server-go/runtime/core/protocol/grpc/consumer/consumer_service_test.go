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
	"testing"
)

func Test_Subscribe(t *testing.T) {
	//mockctl := gomock.NewController(t)
	//mockConsumer := mocks.NewMockConsumerServiceServer(mockctl)
	//response := pb.Response{}
	//mockConsumer.EXPECT().Subscribe(context.TODO(), &pb.Subscription{
	//	Header: &pb.RequestHeader{
	//		Env:             "grpc-env",
	//		Region:          "sh",
	//		Idc:             "idc-sh",
	//		Ip:              util.GetIP(),
	//		Pid:             util.PID(),
	//		Sys:             "grpc-sys",
	//		Username:        "grpc-username",
	//		Password:        "grpc-passwd",
	//		Language:        "Go",
	//		ProtocolType:    "cloudevents",
	//		ProtocolVersion: "1.0",
	//		ProtocolDesc:    "cloudevents",
	//	},
	//	ConsumerGroup: "grpc-stream-consumergroup",
	//	SubscriptionItems: []*pb.Subscription_SubscriptionItem{
	//		{
	//			Topic: "test_topic",
	//			Mode:  pb.Subscription_SubscriptionItem_CLUSTERING,
	//			Type:  pb.Subscription_SubscriptionItem_SYNC,
	//		},
	//	},
	//	Url: "http://127.0.0.1:18080/onmessage",
	//}).Return(&response, nil)
}

func Test_unsubscribe(t *testing.T) {
	//cli := grpc.newTestClient(t)
	//assert.NotNil(t, cli)
	//resp, err := cli.consumerClient.Unsubscribe(context.TODO(), &pb.Subscription{
	//	Header: &pb.RequestHeader{
	//		Env:             "grpc-env",
	//		Region:          "sh",
	//		Idc:             "idc-sh",
	//		Ip:              util.GetIP(),
	//		Pid:             util.PID(),
	//		Sys:             "grpc-sys",
	//		Username:        "grpc-username",
	//		Password:        "grpc-passwd",
	//		Language:        "Go",
	//		ProtocolType:    "cloudevents",
	//		ProtocolVersion: "1.0",
	//		ProtocolDesc:    "cloudevents",
	//	},
	//	ConsumerGroup: "grpc-stream-consumergroup",
	//	SubscriptionItems: []*pb.Subscription_SubscriptionItem{
	//		{
	//			Topic: grpc._testWebhookTopic,
	//			Mode:  pb.Subscription_SubscriptionItem_CLUSTERING,
	//			Type:  pb.Subscription_SubscriptionItem_SYNC,
	//		},
	//	},
	//	Url: "http://127.0.0.1:18080/onmessage",
	//})
	//assert.NoError(t, err)
	//assert.NotNil(t, cli)
	//t.Log(resp.String())
	//assert.Equal(t, resp.RespCode, 0)
}

func Test_subscribeStream(t *testing.T) {
	//cli := grpc.newTestClient(t)
	//assert.NotNil(t, cli)
	//
	//stream, err := cli.consumerClient.SubscribeStream(context.TODO())
	//assert.NoError(t, err)
	//err = stream.Send(&pb.Subscription{
	//	Header: &pb.RequestHeader{
	//		Env:             "grpc-env",
	//		Region:          "sh",
	//		Idc:             "idc-sh",
	//		Ip:              util.GetIP(),
	//		Pid:             util.PID(),
	//		Sys:             "grpc-sys",
	//		Username:        "grpc-username",
	//		Password:        "grpc-passwd",
	//		Language:        "Go",
	//		ProtocolType:    "cloudevents",
	//		ProtocolVersion: "1.0",
	//		ProtocolDesc:    "cloudevents",
	//	},
	//	ConsumerGroup: "grpc-stream-consumergroup",
	//	SubscriptionItems: []*pb.Subscription_SubscriptionItem{
	//		{
	//			Topic: grpc._testStreamTopic,
	//			Mode:  pb.Subscription_SubscriptionItem_CLUSTERING,
	//			Type:  pb.Subscription_SubscriptionItem_ASYNC,
	//		},
	//	},
	//})
	//assert.NoError(t, err)
	//time.Sleep(time.Hour)
}
