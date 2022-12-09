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
	"context"
	"time"

	ce "github.com/cloudevents/sdk-go/v2"

	"github.com/apache/incubator-eventmesh/eventmesh-server-go/plugin"
)

const (
	PluginType = "connector"
)

// Factory plugin factory of consumer/producer/resource
type Factory interface {
	plugin.Plugin

	GetConsumer() (Consumer, error)
	GetProducer() (Producer, error)
	GetResource() (Resource, error)
}

// EventMeshAction commit action of message consume
type EventMeshAction uint

const (
	CommitMessage EventMeshAction = iota
	ReconsumeLater
	ManualAck
)

// Consumer interface of consumer
// all the consumers implement this interface should implement a corresponding factory and do plugin registration first.
type Consumer interface {
	LifeCycle

	InitConsumer(properties map[string]string) error
	UpdateOffset(ctx context.Context, events []*ce.Event) error
	Subscribe(topicName string) error
	Unsubscribe(topicName string) error
	RegisterEventListener(listener *EventListener)
}

// Producer interface of producer
// all the producers implement this interface should implement a corresponding factory and do plugin registration first.
type Producer interface {
	LifeCycle

	InitProducer(properties map[string]string) error
	Publish(ctx context.Context, event *ce.Event, callback *SendCallback) error
	SendOneway(ctx context.Context, event *ce.Event) error
	Request(ctx context.Context, event *ce.Event, callback *RequestReplyCallback, timeout time.Duration) error
	Reply(ctx context.Context, event *ce.Event, callback *SendCallback) error
	CheckTopicExist(topicName string) (bool, error)
	SetExtFields() error
}

// Resource interface of resource service
// all the resources implement this interface should implement a corresponding factory and do plugin registration first.
type Resource interface {
	Init() error
	Release() error
}

// LifeCycle general life cycle interface for all connectors
type LifeCycle interface {
	IsStarted() bool
	IsClosed() bool
	Start() error
	Shutdown() error
}

// SendCallback send callback handler of function Publish
type SendCallback struct {
	OnSuccess func(result *SendResult)
	OnError   func(result *ErrorResult)
}

// RequestReplyCallback request/reply callback handler of function Request and Reply
type RequestReplyCallback struct {
	OnSuccess func(event *ce.Event)
	OnError   func(result *ErrorResult)
}

type SendResult struct {
	MessageId string
	Topic     string
	Err       error
}

type ErrorResult struct {
	Topic string
	Err   error
}

// EventListener message consume handler
type EventListener struct {
	Consume ConsumeFunc
}

// CommitFunc user can commit message through this function in the consuming logic
type CommitFunc func(action EventMeshAction) error

// ConsumeFunc custom message consuming logic
type ConsumeFunc func(event *ce.Event, commitFunc CommitFunc) error
