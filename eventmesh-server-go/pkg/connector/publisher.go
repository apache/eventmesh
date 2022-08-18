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

package connector

import (
	cloudevents "github.com/cloudevents/sdk-go/v2"
)

type SendResult struct {
	MessageId string `json:"messageId"`
	Mopic     string `json:"topic"`
}

type SendErrResult struct {
	*SendResult
	Err error `json:"err"`
}

// SendCallback callback for send message to eventmesh
type SendCallback interface {
	OnSuccess(*SendResult)

	OnError(*SendErrResult)
}

type RequestReplyCallback interface {
	OnSuccess(*SendResult)

	OnError(*SendErrResult)
}

// Consumer message for eventmesh standalone connector
type Producer interface {
	LifeCycle

	Initialize(*Properties) error

	Publish(cloudevents.Event, SendCallback) error

	SendOneway(cloudevents.Event) error

	Request(cloudevents.Event, RequestReplyCallback, time.Duration) error

	Reply(cloudevents.Event, SendCallback) error

	CheckTopicExist(string) bool

	SetExtFields();
}
