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
	"github.com/apache/rocketmq-client-go/v2/primitive"
	ce "github.com/cloudevents/sdk-go/v2"
	"github.com/cloudevents/sdk-go/v2/binding"
	"github.com/cloudevents/sdk-go/v2/binding/spec"
	"github.com/cloudevents/sdk-go/v2/types"
	"io"
	"io/ioutil"
)

// RocketMQMessageWriter cloud event message writer
type RocketMQMessageWriter struct {
	message *primitive.Message
}

// NewRocketMQMessageWriter get RocketMQ message writer
func NewRocketMQMessageWriter(topic string) *RocketMQMessageWriter {
	message := &primitive.Message{}
	message.Topic = topic
	return &RocketMQMessageWriter{message: message}
}

// SetAttribute set attribute
func (r *RocketMQMessageWriter) SetAttribute(attribute spec.Attribute, value interface{}) error {
	val, err := types.Format(value)
	if err != nil {
		return err
	}
	r.message.WithProperty(attribute.Name(), val)
	return nil
}

// SetExtension set extension
func (r *RocketMQMessageWriter) SetExtension(name string, value interface{}) error {
	r.message.WithProperty(name, value.(string))
	return nil
}

// Start to start writing, do nothing
func (r *RocketMQMessageWriter) Start(ctx context.Context) error {
	// No-Op
	return nil
}

// SetData set data from reader
func (r *RocketMQMessageWriter) SetData(data io.Reader) error {
	b, err := ioutil.ReadAll(data)
	if err != nil {
		return nil
	}
	r.message.Body = b
	return nil
}

// End the end of writing, do nothing
func (r *RocketMQMessageWriter) End(ctx context.Context) error {
	// No-Op
	return nil
}

// ToMessage convert cloud event to RocketMQ message
func (r *RocketMQMessageWriter) ToMessage(ctx context.Context, cloudEvent *ce.Event) (*primitive.Message, error) {
	_, err := binding.Write(binding.WithForceBinary(ctx), (*binding.EventMessage)(cloudEvent), nil, r)
	if err != nil {
		return nil, err
	}
	return r.message, err
}
