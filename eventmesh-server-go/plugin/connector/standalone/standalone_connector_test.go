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

package standalone

import (
	"context"
	"fmt"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/plugin"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/plugin/connector"
	ce "github.com/cloudevents/sdk-go/v2"
	"github.com/google/uuid"
	"github.com/stretchr/testify/assert"
	"gopkg.in/yaml.v3"
	"testing"
)

const (
	topicName  = "example-topic"
	pluginName = "standalone"
)

// MockDecoder standalone connector properties mock decoder
type MockDecoder struct {
}

// Decode mock decoder, no-op
func (m *MockDecoder) Decode(cfg interface{}) error {
	return nil
}

func TestProducer_Publish(t *testing.T) {
	factory := &Factory{}
	err := factory.Setup(pluginName, &plugin.YamlNodeDecoder{
		Node: &yaml.Node{},
	})
	assert.NoError(t, err)
	producer, _ := factory.GetProducer()
	producer.Start()
	defer producer.Shutdown()

	var publishSuccess bool
	var callBackErr error
	topic := fmt.Sprintf("%s_publish", topicName)
	callback := connector.SendCallback{
		OnSuccess: func(result *connector.SendResult) {
			publishSuccess = true
			assert.Equal(t, topic, result.Topic)
			assert.Equal(t, "1", result.MessageId)
			assert.Nil(t, result.Err)
		},
		OnError: func(result *connector.ErrorResult) {
			callBackErr = result.Err
		},
	}

	err = producer.Publish(context.Background(), getTestEvent(topic), &callback)
	assert.Nil(t, err)
	assert.True(t, publishSuccess)
	assert.Nil(t, callBackErr)

	exist, err := producer.CheckTopicExist(topic)
	assert.True(t, exist)
	assert.Nil(t, err)

}
func TestConsumer_Subscribe(t *testing.T) {
	done := make(chan struct{})
	topic := fmt.Sprintf("%s_subscribe", topicName)
	listener := connector.EventListener{
		Consume: func(event *ce.Event, commitFunc connector.CommitFunc) error {
			var data map[string]interface{}
			event.DataAs(&data)
			t.Log(event.String())
			commitFunc(connector.CommitMessage)
			done <- struct{}{}
			return nil
		},
	}

	factory := &Factory{}
	err := factory.Setup(pluginName, &plugin.YamlNodeDecoder{
		Node: &yaml.Node{},
	})
	assert.NoError(t, err)
	consumer, _ := factory.GetConsumer()
	consumer.Start()
	consumer.RegisterEventListener(&listener)
	consumer.Subscribe(topic)
	defer consumer.Shutdown()

	producer, _ := factory.GetProducer()
	producer.Start()
	defer producer.Shutdown()
	err = producer.Publish(context.Background(), getTestEventOfData(topic, map[string]interface{}{
		"val": "value",
	}), getEmptyPublishCallback())
	assert.NoError(t, err)
	<-done
}

func TestConsumer_ManualAck(t *testing.T) {
	done := make(chan struct{})
	topic := fmt.Sprintf("%s_ack", topicName)
	listener := connector.EventListener{
		Consume: func(event *ce.Event, commitFunc connector.CommitFunc) error {
			var data map[string]interface{}
			event.DataAs(&data)
			commitFunc(connector.ManualAck)
			done <- struct{}{}
			return nil
		},
	}

	factory := &Factory{}
	err := factory.Setup(pluginName, &plugin.YamlNodeDecoder{
		Node: &yaml.Node{},
	})
	assert.NoError(t, err)
	consumer, _ := factory.GetConsumer()
	consumer.Start()
	consumer.RegisterEventListener(&listener)
	consumer.Subscribe(topic)
	defer consumer.Shutdown()

	producer, _ := factory.GetProducer()
	producer.Start()
	defer producer.Shutdown()
	err = producer.Publish(context.Background(), getTestEventOfData(topic, map[string]interface{}{
		"val": "test",
	}), getEmptyPublishCallback())
	assert.NoError(t, err)
	<-done
}

// TODO update later
func TestConsumer_UpdateOffset(t *testing.T) {
	//sum := atomic.NewInt64(0)
	//ch := make(chan struct{})
	//listener := connector.EventListener{
	//	Consume: func(event *ce.Event, commitFunc connector.CommitFunc) error {
	//		var data map[string]interface{}
	//		event.DataAs(&data)
	//		sum.Add(int64(data["val"].(float64)))
	//		commitFunc(connector.CommitMessage)
	//		ch <- struct{}{}
	//		return nil
	//	},
	//}
	//
	//factory := &Factory{}
	//err := factory.Setup(pluginName, &plugin.YamlNodeDecoder{
	//	Node: &yaml.Node{},
	//})
	//assert.NoError(t, err)
	//consumer, _ := factory.GetConsumer()
	//consumer.Start()
	//defer consumer.Shutdown()
	//consumer.RegisterEventListener(&listener)
	//event := getTestEvent()
	//event.SetExtension("offset", "49")
	//consumer.Subscribe(topicName)
	//consumer.UpdateOffset(context.Background(), []*ce.Event{event})
	//
	//producer, _ := factory.GetProducer()
	//producer.Start()
	//defer producer.Shutdown()
	//for i := 1; i <= 50; i++ {
	//	err := producer.Publish(context.Background(), getTestEventOfData(map[string]interface{}{
	//		"val": i,
	//	}), getEmptyPublishCallback())
	//
	//	if err != nil {
	//		t.Fail()
	//		return
	//	}
	//}
	//
	//timer := time.NewTimer(3 * time.Second)
	//select {
	//case <-timer.C:
	//	t.Fail()
	//case <-ch:
	//	assert.Equal(t, int64(50), sum.Load())
	//}
}

func getTestEvent(topicName string) *ce.Event {
	event := ce.NewEvent()
	event.SetID(uuid.New().String())
	event.SetSubject(topicName)
	return &event
}

func getTestEventOfData(topicName string, data map[string]interface{}) *ce.Event {
	event := ce.NewEvent()
	event.SetID(uuid.New().String())
	event.SetSubject(topicName)
	event.SetData(ce.ApplicationJSON, data)
	return &event
}

func getEmptyPublishCallback() *connector.SendCallback {
	return &connector.SendCallback{
		OnSuccess: func(result *connector.SendResult) {
			// No-Op
		},
		OnError: func(result *connector.ErrorResult) {
			panic(result.Err)
		},
	}
}
