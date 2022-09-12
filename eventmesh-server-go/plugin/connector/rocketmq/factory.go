package rocketmq

import (
	"errors"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/plugin"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/plugin/connector"
)

func init() {
	plugin.Register("rocketmq", &ProducerFactory{})
	plugin.Register("rocketmq", &ResourceFactory{})
}

type ProducerFactory struct {
	plugin.Plugin
	properties map[string]string
}

func (f *ProducerFactory) Type() string {
	return connector.ProducerPluginType
}

func (f *ProducerFactory) Setup(name string, dec plugin.Decoder) error {
	if dec == nil {
		return errors.New(" producer config decoder empty")
	}
	properties := make(map[string]string)
	if err := dec.Decode(properties); err != nil {
		return err
	}
	f.properties = properties
	return nil
}

func (f *ProducerFactory) Get() (connector.Producer, error) {
	producer := NewProducer()
	err := producer.InitProducer(f.properties)
	if err != nil {
		return nil, err
	}
	err = producer.Start()
	if err != nil {
		return nil, err
	}
	return producer, nil
}

type ResourceFactory struct {
	plugin.Plugin
}

func (f *ResourceFactory) Type() string {
	return connector.ResourcePluginType
}

func (f *ResourceFactory) Setup(name string, dec plugin.Decoder) error {
	return nil
}

func (f *ResourceFactory) Get() (connector.Resource, error) {
	return &Resource{}, nil
}
