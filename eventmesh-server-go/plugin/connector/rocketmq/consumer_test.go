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

package rocketmq

import (
	"context"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/plugin/connector"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/plugin/connector/rocketmq/client"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/plugin/connector/rocketmq/convert"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/plugin/connector/rocketmq/mock"
	rmq "github.com/apache/rocketmq-client-go/v2/consumer"
	"github.com/apache/rocketmq-client-go/v2/primitive"
	ce "github.com/cloudevents/sdk-go/v2"
	"github.com/golang/mock/gomock"
	"github.com/stretchr/testify/require"
	"testing"
)

func GetConsumerWithMockClient(ctl *gomock.Controller) *Consumer {
	consumer := NewConsumer()
	consumer.InitConsumer(make(map[string]string))
	mockRocketMQConsumer := mock.NewMockRocketMQConsumer(ctl)
	mockRocketMQConsumer.EXPECT().Start().Return(nil)
	mockRocketMQConsumer.EXPECT().Shutdown().Return(nil)
	consumer.rocketMQConsumer = mockRocketMQConsumer
	consumer.subscribeHandler = &ClusteringMessageSubscribeHandler{consumer: consumer}
	return consumer
}

func TestConsumer_Start(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()
	consumer := GetConsumerWithMockClient(ctrl)
	consumer.Start()
	require.True(t, consumer.IsStarted())
	consumer.Shutdown()
}

func TestConsumer_Shutdown(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()
	consumer := GetConsumerWithMockClient(ctrl)
	consumer.Start()
	consumer.Shutdown()
	require.False(t, consumer.IsStarted())
}

func TestConsumer_Subscribe(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()
	consumer := GetConsumerWithMockClient(ctrl)
	consumer.Start()

	listener := &connector.EventListener{
		Consume: func(event *ce.Event, commitFunc connector.CommitFunc) error {
			require.True(t, event.Subject() == "TopicTest")
			commitFunc(connector.CommitMessage)
			return nil
		},
	}

	consumer.RegisterEventListener(listener)

	consumer.rocketMQConsumer.(*mock.MockRocketMQConsumer).EXPECT().
		Subscribe(gomock.Any(), gomock.Any(), gomock.Any()).
		Do(
			func(topic string, selector rmq.MessageSelector, f client.SubscribeFunc) {
				messageExt := &primitive.MessageExt{
					Message:     *GetFullMessage(),
					MsgId:       "MockMsgId",
					OffsetMsgId: "MockOffsetMsgId",
				}
				result, err := f(context.Background(), messageExt)
				require.False(t, err != nil)
				require.True(t, result == rmq.ConsumeSuccess)
			},
		)

	consumer.Subscribe("TopicTest")
	consumer.Shutdown()
}

//func TestConsumer_Subscribe_Broker(t *testing.T) {
//	properties := make(map[string]string)
//	properties["access_points"] = "127.0.0.1:9876"
//	properties["consumer_group"] = "test_group"
//	consumer := NewConsumer()
//	consumer.InitConsumer(properties)
//	consumer.Start()
//
//	listener := &connector.EventListener{
//		Consume: func(event *ce.Event, commitFunc connector.CommitFunc) error {
//			fmt.Println(event)
//			commitFunc(connector.CommitMessage)
//			return nil
//		},
//	}
//
//	consumer.RegisterEventListener(listener)
//	err := consumer.Subscribe("TopicTest")
//	if err != nil {
//		panic(err)
//	}
//
//	time.Sleep(100 * time.Second)
//}

func GetFullMessage() *primitive.Message {
	event := ce.NewEvent()
	event.SetSubject("TopicTest")
	event.SetID("MockEventId")
	event.SetDataContentType("application/json")
	event.SetExtension("protocoltype", "http")
	event.SetData("application/json", "{\"data\":\"foo\"}")
	message, _ := convert.NewRocketMQMessageWriter("test-topic").ToMessage(context.Background(), &event)
	return message
}
