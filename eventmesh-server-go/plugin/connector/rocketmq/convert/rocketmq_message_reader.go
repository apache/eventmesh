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
	"bytes"
	"context"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/plugin/connector/rocketmq/constants"
	"github.com/apache/rocketmq-client-go/v2/primitive"
	ce "github.com/cloudevents/sdk-go/v2"
	"github.com/cloudevents/sdk-go/v2/binding"
	"github.com/cloudevents/sdk-go/v2/binding/spec"
	"github.com/cloudevents/sdk-go/v2/event"
)

// RocketMQMessageReader cloud event message reader
type RocketMQMessageReader struct {
	topic      string
	properties map[string]string
	body       []byte
	version    spec.Version
}

// NewRocketMQMessageReader get RocketMQ message reader
func NewRocketMQMessageReader(message *primitive.Message) *RocketMQMessageReader {
	return &RocketMQMessageReader{
		topic:      message.Topic,
		properties: message.GetProperties(),
		body:       message.Body,
		version:    spec.VS.Version(event.CloudEventsVersionV1),
	}
}

// ReadEncoding the RocketMQMessageReader only supports binary encoding
func (r *RocketMQMessageReader) ReadEncoding() binding.Encoding {
	return binding.EncodingBinary
}

// ReadBinary read message and write through encoder
func (r *RocketMQMessageReader) ReadBinary(ctx context.Context, encoder binding.BinaryWriter) (err error) {
	subject := r.version.Attribute("subject")
	encoder.SetAttribute(subject, r.topic)

	msgType := r.version.Attribute("type")
	encoder.SetAttribute(msgType, constants.CloudEventMessageType)

	contentType := r.version.Attribute("datacontenttype")
	encoder.SetAttribute(contentType, r.properties[constants.RocketMQMessageContentTypeProperties])

	for k, v := range r.properties {
		attr := r.version.Attribute(k)
		if attr != nil {
			err = encoder.SetAttribute(attr, v)
		} else {
			err = encoder.SetExtension(k, v)
		}
		if err != nil {
			return err
		}
	}

	if r.body != nil {
		err = encoder.SetData(bytes.NewReader(r.body))
		if err != nil {
			return err
		}
	}
	return
}

// GetAttribute get attribute
func (r *RocketMQMessageReader) GetAttribute(k spec.Kind) (spec.Attribute, interface{}) {
	attr := r.version.Attribute(k.String())
	if attr != nil {
		return attr, r.properties[k.String()]
	}
	return attr, nil
}

// GetExtension get extension
func (r *RocketMQMessageReader) GetExtension(s string) interface{} {
	return r.properties[s]
}

// ReadStructured the RocketMQMessageReader only supports binary encoding
func (r *RocketMQMessageReader) ReadStructured(context.Context, binding.StructuredWriter) error {
	return binding.ErrNotStructured
}

// ToCloudEvent convert RocketMQ message to cloud event
func (r *RocketMQMessageReader) ToCloudEvent(ctx context.Context) (*ce.Event, error) {
	event, err := binding.ToEvent(ctx, r)
	if err != nil {
		return nil, err
	}
	return event, nil
}
