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
	"github.com/apache/eventmesh/eventmesh-sdk-go/common/protocol"
	cloudevents "github.com/cloudevents/sdk-go/v2"
	"time"
)

type EventMeshProtocolProducer interface {

	// PublishCloudEvent publish with CloudEvent protocol
	PublishCloudEvent(event *cloudevents.Event) error
	// RequestCloudEvent request with CloudEvent protocol
	RequestCloudEvent(event *cloudevents.Event, timeout time.Duration) (*cloudevents.Event, error)

	// PublishEventMeshMessage publish with EventMeshMessage protocol
	PublishEventMeshMessage(message *protocol.EventMeshMessage) error
	// RequestEventMeshMessage request with EventMeshMessage protocol
	RequestEventMeshMessage(message *protocol.EventMeshMessage, timeout time.Duration) (*protocol.EventMeshMessage, error)

	// TODO: add OpenMessage support
}
