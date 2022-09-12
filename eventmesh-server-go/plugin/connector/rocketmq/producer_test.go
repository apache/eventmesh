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
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/plugin/connector/rocketmq/mock"
	"github.com/apache/rocketmq-client-go/v2/primitive"
	ce "github.com/cloudevents/sdk-go/v2"
	"github.com/cloudevents/sdk-go/v2/event"
	"github.com/cloudevents/sdk-go/v2/event/datacodec"
	"github.com/golang/mock/gomock"
	"github.com/stretchr/testify/require"
	"testing"
	"time"
)

func GetProducerWithMockClient(ctl *gomock.Controller) *Producer {
	producer := NewProducer()
	producer.InitProducer(make(map[string]string))
	mockRocketMQProducer := mock.NewMockRocketMQProducer(ctl)
	mockRocketMQProducer.EXPECT().Start().Return(nil)
	mockRocketMQProducer.EXPECT().Shutdown().Return(nil)
	producer.rocketMQProducer = mockRocketMQProducer
	return producer
}

func TestProducer_Start(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()
	producer := GetProducerWithMockClient(ctrl)
	producer.Start()
	require.True(t, producer.IsStarted())
	producer.Shutdown()
}

func TestProducer_Shutdown(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()
	producer := GetProducerWithMockClient(ctrl)
	producer.Start()
	producer.Shutdown()
	require.False(t, producer.IsStarted())
}

func TestProducer_Publish(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()
	producer := GetProducerWithMockClient(ctrl)
	producer.Start()

	callback := &connector.SendCallback{
		OnSuccess: func(result *connector.SendResult) {
			require.True(t, result.Topic == "TopicTest")
			require.True(t, result.MessageId == "MockMsgID")
		},
		OnError: func(result *connector.ErrorResult) {
			panic(result.Err)
		},
	}

	producer.rocketMQProducer.(*mock.MockRocketMQProducer).EXPECT().
		SendAsync(gomock.Any(), gomock.Any(), gomock.Any()).Do(
		func(ctx, mq interface{}, msg ...interface{}) {
			result := &primitive.SendResult{
				Status: primitive.SendOK,
				MsgID:  "MockMsgID",
				MessageQueue: &primitive.MessageQueue{
					Topic: "TopicTest",
				},
			}
			mq.(func(ctx context.Context, result *primitive.SendResult, err error))(context.Background(), result, nil)
		},
	)
	err := producer.Publish(context.Background(), GetFullEvent(), callback)
	require.True(t, err == nil)
	producer.Shutdown()
}

func TestProducer_SendOneway(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()
	producer := GetProducerWithMockClient(ctrl)
	producer.Start()

	producer.rocketMQProducer.(*mock.MockRocketMQProducer).EXPECT().
		SendOneWay(gomock.Any(), gomock.Any()).Do(
		func(ctx context.Context, mq ...*primitive.Message) {
			encodedData, _ := datacodec.Encode(context.Background(), event.ApplicationJSON, "{\"data\":\"foo\"}")
			require.True(t, len(mq) == 1)
			require.True(t, mq[0].Topic == "TopicTest")
			require.True(t, string(mq[0].Body) == string(encodedData))
		},
	)
	err := producer.SendOneway(context.Background(), GetFullEvent())
	require.True(t, err == nil)
	producer.Shutdown()
}

func TestProducer_Request(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()
	producer := GetProducerWithMockClient(ctrl)
	producer.Start()

	callback := &connector.RequestReplyCallback{
		OnSuccess: func(event *ce.Event) {
			require.True(t, event.Subject() == "TopicTest")
			require.True(t, string(event.Data()) == "{\"data\":\"resp\"}")
		},
		OnError: func(result *connector.ErrorResult) {
			panic(result.Err)
		},
	}

	producer.rocketMQProducer.(*mock.MockRocketMQProducer).EXPECT().
		RequestAsync(gomock.Any(), gomock.Any(), gomock.Any(), gomock.Any()).Do(
		func(ctx context.Context, ttl time.Duration,
			callback func(ctx context.Context, msg *primitive.Message, err error),
			msg *primitive.Message) {
			responseMessage := &primitive.Message{
				Topic: "TopicTest",
				Body:  []byte("{\"data\":\"resp\"}"),
			}
			callback(context.Background(), responseMessage, nil)
		},
	)
	err := producer.Request(context.Background(), GetFullEvent(), callback, time.Second)
	require.True(t, err == nil)
	producer.Shutdown()
}

func TestProducer_Reply(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()
	producer := GetProducerWithMockClient(ctrl)
	producer.Start()

	callback := &connector.SendCallback{
		OnSuccess: func(result *connector.SendResult) {
			require.True(t, result.Topic == "TopicTest")
			require.True(t, result.MessageId == "MockMsgID")
		},
		OnError: func(result *connector.ErrorResult) {
			panic(result.Err)
		},
	}
	producer.rocketMQProducer.(*mock.MockRocketMQProducer).EXPECT().
		SendAsync(gomock.Any(), gomock.Any(), gomock.Any()).Do(
		func(ctx, mq interface{}, msg ...interface{}) {
			result := &primitive.SendResult{
				Status: primitive.SendOK,
				MsgID:  "MockMsgID",
				MessageQueue: &primitive.MessageQueue{
					Topic: "TopicTest",
				},
			}
			mq.(func(ctx context.Context, result *primitive.SendResult, err error))(context.Background(), result, nil)
		},
	)
	err := producer.Reply(context.Background(), GetFullEvent(), callback)
	require.True(t, err == nil)
	producer.Shutdown()
}

func GetFullEvent() *ce.Event {
	event := ce.NewEvent()
	event.SetSubject("TopicTest")
	event.SetID("MockEventId")
	event.SetDataContentType("application/json")
	event.SetExtension("protocoltype", "http")
	event.SetData("application/json", "{\"data\":\"foo\"}")
	return &event
}

//func TestProducer_Publish_Broker(t *testing.T) {
//	properties := make(map[string]string)
//	properties["access_points"] = "127.0.0.1:9876"
//	producer := NewProducer()
//	producer.InitProducer(properties)
//	producer.Start()
//
//	var wg sync.WaitGroup
//	wg.Add(1)
//	callback := &connector.SendCallback{
//		OnSuccess: func(result *connector.SendResult) {
//			require.True(t, result.Topic == "TopicTest")
//			wg.Done()
//		},
//		OnError: func(result *connector.ErrorResult) {
//			panic(result.Err)
//		},
//	}
//
//	err := producer.Publish(context.Background(), GetFullEvent(), callback)
//	require.True(t, err == nil)
//
//	wg.Wait()
//	producer.Shutdown()
//}
