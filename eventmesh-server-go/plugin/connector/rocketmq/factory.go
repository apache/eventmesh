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

package rocketmq

import (
	"errors"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/plugin"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/plugin/connector"
)

func init() {
	plugin.Register("rocketmq", &Factory{})
}

type Factory struct {
	plugin.Plugin
	properties map[string]string
	producer   connector.Producer
	consumer   connector.Consumer
}

func (f *Factory) Type() string {
	return connector.PluginType
}

func (f *Factory) Setup(name string, dec plugin.Decoder) error {
	if dec == nil {
		return errors.New(" producer config decoder empty")
	}
	properties := make(map[string]string)
	if err := dec.Decode(properties); err != nil {
		return err
	}
	f.properties = properties

	consumer := NewConsumer()
	err := consumer.InitConsumer(f.properties)
	if err != nil {
		return err
	}
	err = consumer.Start()
	if err != nil {
		return err
	}
	f.consumer = consumer

	producer := NewProducer()
	err = producer.InitProducer(f.properties)
	if err != nil {
		return err
	}
	err = producer.Start()
	if err != nil {
		return err
	}
	f.producer = producer

	return nil
}

func (f *Factory) GetProducer() (connector.Producer, error) {
	return f.producer, nil
}

func (f *Factory) GetConsumer() (connector.Consumer, error) {
	return f.consumer, nil
}

func (f *Factory) GetResource() (connector.Resource, error) {
	return &Resource{}, nil
}
