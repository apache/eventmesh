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

package convert

import (
	"context"
	ce "github.com/cloudevents/sdk-go/v2"
	"github.com/cloudevents/sdk-go/v2/event"
	"github.com/cloudevents/sdk-go/v2/event/datacodec"
	"github.com/stretchr/testify/require"
	"testing"
)

func TestWriteBinary(t *testing.T) {
	message, err := NewRocketMQMessageWriter("test-topic").ToMessage(context.Background(), getTestEvent())
	require.True(t, err == nil)
	require.True(t, message.GetProperty("id") == "MockEventId")
	require.True(t, message.GetProperty("protocoltype") == "http")
	data, _ := datacodec.Encode(context.Background(), event.ApplicationJSON, "{\"data\":\"foo\"}")
	require.True(t, string(message.Body) == string(data))
}

func getTestEvent() *ce.Event {
	event := ce.NewEvent()
	event.SetSubject("TopicTest")
	event.SetID("MockEventId")
	event.SetDataContentType("application/json")
	event.SetExtension("protocoltype", "http")
	event.SetData("application/json", "{\"data\":\"foo\"}")
	return &event
}
