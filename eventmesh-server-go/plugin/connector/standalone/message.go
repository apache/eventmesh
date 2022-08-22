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
	ce "github.com/cloudevents/sdk-go/v2"
	"strconv"
)

type Message struct {
	createTimeMills int64
	event           *ce.Event
}

func (m *Message) GetTopicName() string {
	return m.event.Subject()
}

func (m *Message) GetOffset() int64 {
	return GetOffsetFromEvent(m.event)
}

// SetOffset since the CloudEvents go-sdk doesn't support int64 in v1 version, need to store offset by string
func (m *Message) SetOffset(offset int64) {
	m.event.SetExtension("offset", strconv.FormatInt(offset, 10))
}

func GetOffsetFromEvent(event *ce.Event) int64 {
	offset, err := strconv.ParseInt(event.Extensions()["offset"].(string), 10, 64)
	if err != nil {
		panic(err)
	}
	return offset
}
