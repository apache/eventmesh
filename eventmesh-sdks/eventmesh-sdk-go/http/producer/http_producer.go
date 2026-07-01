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

package producer

import (
	"errors"
	"github.com/apache/eventmesh/eventmesh-sdk-go/common/protocol"
	"github.com/apache/eventmesh/eventmesh-sdk-go/http/conf"
	cloudevents "github.com/cloudevents/sdk-go/v2"
	"time"
)

type EventMeshHttpProducer struct {
	cloudEventProducer       *CloudEventProducer
	eventMeshMessageProducer *EventMeshMessageProducer
}

func NewEventMeshHttpProducer(eventMeshHttpClientConfig conf.EventMeshHttpClientConfig) *EventMeshHttpProducer {
	return &EventMeshHttpProducer{
		cloudEventProducer:       NewCloudEventProducer(eventMeshHttpClientConfig),
		eventMeshMessageProducer: NewEventMeshMessageProducer(eventMeshHttpClientConfig),
	}
}

func (e *EventMeshHttpProducer) PublishCloudEvent(event *cloudevents.Event) error {
	if event == nil {
		return errors.New("publish CloudEvent message failed, message is nil")
	}
	return e.cloudEventProducer.Publish(event)
}

func (e *EventMeshHttpProducer) RequestCloudEvent(event *cloudevents.Event, timeout time.Duration) (*cloudevents.Event, error) {
	if event == nil {
		return nil, errors.New("request CloudEvent message failed, message is nil")
	}
	return e.cloudEventProducer.Request(event, timeout)
}

func (e *EventMeshHttpProducer) PublishEventMeshMessage(message *protocol.EventMeshMessage) error {
	if message == nil {
		return errors.New("publish EventMesh message failed, message is nil")
	}
	return e.eventMeshMessageProducer.Publish(message)
}

func (e *EventMeshHttpProducer) RequestEventMeshMessage(message *protocol.EventMeshMessage, timeout time.Duration) (*protocol.EventMeshMessage, error) {
	if message == nil {
		return nil, errors.New("request EventMesh message failed, message is nil")
	}
	return e.eventMeshMessageProducer.Request(message, timeout)
}
