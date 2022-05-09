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

package grpc

import (
	"fmt"
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/common/utils"
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/grpc/conf"
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/grpc/proto"
	"time"
)

// SimpleMessageBuilder used to build the simple message
type SimpleMessageBuilder struct {
	*proto.SimpleMessage
}

// NewMessageBuilder
func NewMessageBuilder() *SimpleMessageBuilder {
	return &SimpleMessageBuilder{SimpleMessage: &proto.SimpleMessage{}}
}

// WithHeader set the header for message
func (m *SimpleMessageBuilder) WithHeader(h *proto.RequestHeader) *SimpleMessageBuilder {
	m.Header = h
	return m
}

// WithProducerGroup set the message producer group
func (m *SimpleMessageBuilder) WithProducerGroup(grp string) *SimpleMessageBuilder {
	m.ProducerGroup = grp
	return m
}

// WithTopic set the topic
func (m *SimpleMessageBuilder) WithTopic(topic string) *SimpleMessageBuilder {
	m.Topic = topic
	return m
}

// WithContent set the content to message
func (m *SimpleMessageBuilder) WithContent(content string) *SimpleMessageBuilder {
	m.Content = content
	return m
}

// WithTTL set the message ttl
func (m *SimpleMessageBuilder) WithTTL(ttl time.Duration) *SimpleMessageBuilder {
	m.Ttl = fmt.Sprintf("%v", ttl.Seconds())
	return m
}

// WithTag set the tag for message
func (m *SimpleMessageBuilder) WithTag(tag string) *SimpleMessageBuilder {
	m.Tag = tag
	return m
}

// WithUniqueID set the uniq id for message
func (m *SimpleMessageBuilder) WithUniqueID(id string) *SimpleMessageBuilder {
	m.UniqueId = id
	return m
}

// WithSeqNO set the sequence no for message
func (m *SimpleMessageBuilder) WithSeqNO(no string) *SimpleMessageBuilder {
	m.SeqNum = no
	return m
}

// WithProperties set the properties for message
func (m *SimpleMessageBuilder) WithProperties(props map[string]string) *SimpleMessageBuilder {
	m.Properties = props
	return m
}

// CreateHeader create msg header
func CreateHeader(cfg *conf.GRPCConfig) *proto.RequestHeader {
	return &proto.RequestHeader{
		Env:             cfg.ENV,
		Region:          cfg.Region,
		Idc:             cfg.IDC,
		Ip:              utils.HostIPV4(),
		Pid:             utils.CurrentPID(),
		Sys:             cfg.SYS,
		Username:        cfg.Username,
		Password:        cfg.Password,
		Language:        conf.Language,
		ProtocolType:    EventmeshMessage,
		ProtocolDesc:    conf.ProtocolDesc,
		ProtocolVersion: conf.ProtocolVersion,
	}
}

// GetTTLWithDefault return the ttl for the given msg, if err occurred return default
func GetTTLWithDefault(msg *proto.SimpleMessage, def time.Duration) time.Duration {
	if msg == nil {
		return def
	}
	val, ok := msg.Properties[EVENTMESH_MESSAGE_CONST_TTL]
	if !ok {
		return def
	}

	tm, err := time.ParseDuration(val)
	if err != nil {
		return def
	}
	return tm
}
