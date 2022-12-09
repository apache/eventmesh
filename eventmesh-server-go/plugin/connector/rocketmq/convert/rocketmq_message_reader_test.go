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
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/plugin/connector/rocketmq/constants"
	"github.com/apache/rocketmq-client-go/v2/primitive"
	"github.com/cloudevents/sdk-go/v2/event"
	"github.com/cloudevents/sdk-go/v2/event/datacodec"
	"github.com/stretchr/testify/require"
	"testing"
)

func TestRocketMQMessageReader_ToCloudEvent(t *testing.T) {
	e, err := NewRocketMQMessageReader(getTestMessage()).ToCloudEvent(context.Background())
	require.True(t, err == nil)
	require.True(t, e.ID() == "MockEventId")
	require.True(t, e.Extensions()["protocoltype"] == "http")
	data, _ := datacodec.Encode(context.Background(), event.ApplicationJSON, "{\"data\":\"foo\"}")
	require.True(t, string(e.Data()) == string(data))
}

func getTestMessage() *primitive.Message {
	data, _ := datacodec.Encode(context.Background(), event.ApplicationJSON, "{\"data\":\"foo\"}")
	message := &primitive.Message{
		Topic: "TopicTest",
		Body:  data,
	}
	message.WithProperty("id", "MockEventId")
	message.WithProperty(constants.RocketMQMessageContentTypeProperties, event.ApplicationJSON)
	message.WithProperty("protocoltype", "http")
	return message
}
