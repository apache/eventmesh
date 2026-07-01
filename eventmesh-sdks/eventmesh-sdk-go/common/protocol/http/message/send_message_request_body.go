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

package message

type SendMessageRequestBody struct {
	topic         string
	bizSeqNo      string
	uniqueId      string
	ttl           string
	content       string
	tag           string
	extFields     map[string]string
	producerGroup string
}

func (s *SendMessageRequestBody) Topic() string {
	return s.topic
}

func (s *SendMessageRequestBody) SetTopic(topic string) {
	s.topic = topic
}

func (s *SendMessageRequestBody) BizSeqNo() string {
	return s.bizSeqNo
}

func (s *SendMessageRequestBody) SetBizSeqNo(bizSeqNo string) {
	s.bizSeqNo = bizSeqNo
}

func (s *SendMessageRequestBody) UniqueId() string {
	return s.uniqueId
}

func (s *SendMessageRequestBody) SetUniqueId(uniqueId string) {
	s.uniqueId = uniqueId
}

func (s *SendMessageRequestBody) Ttl() string {
	return s.ttl
}

func (s *SendMessageRequestBody) SetTtl(ttl string) {
	s.ttl = ttl
}

func (s *SendMessageRequestBody) Content() string {
	return s.content
}

func (s *SendMessageRequestBody) SetContent(content string) {
	s.content = content
}

func (s *SendMessageRequestBody) Tag() string {
	return s.tag
}

func (s *SendMessageRequestBody) SetTag(tag string) {
	s.tag = tag
}

func (s *SendMessageRequestBody) ExtFields() map[string]string {
	return s.extFields
}

func (s *SendMessageRequestBody) SetExtFields(extFields map[string]string) {
	s.extFields = extFields
}

func (s *SendMessageRequestBody) ProducerGroup() string {
	return s.producerGroup
}

func (s *SendMessageRequestBody) SetProducerGroup(producerGroup string) {
	s.producerGroup = producerGroup
}

var SendMessageRequestBodyKey = struct {
	TOPIC         string
	BIZSEQNO      string
	UNIQUEID      string
	CONTENT       string
	TTL           string
	TAG           string
	EXTFIELDS     string
	PRODUCERGROUP string
}{
	TOPIC:         "topic",
	BIZSEQNO:      "bizseqno",
	UNIQUEID:      "uniqueid",
	CONTENT:       "content",
	TTL:           "ttl",
	TAG:           "tag",
	EXTFIELDS:     "extFields",
	PRODUCERGROUP: "producergroup",
}
