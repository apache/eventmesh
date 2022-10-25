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
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/log"
	"sync"
)

// ProducerManager manger for all producer
type ProducerManager struct {
	// EventMeshProducers {groupName, *EventMeshProducer}
	EventMeshProducers *sync.Map
}

func NewProducerManager() (*ProducerManager, error) {
	return &ProducerManager{
		EventMeshProducers: new(sync.Map),
	}, nil
}

func (m *ProducerManager) GetProducer(groupName string) (*EventMeshProducer, error) {
	p, ok := m.EventMeshProducers.Load(groupName)
	if ok {
		return p.(*EventMeshProducer), nil
	}
	pgc := &ProducerGroupConfig{GroupName: groupName}
	pg, err := m.CreateProducer(pgc)
	if err != nil {
		return nil, err
	}
	return pg, nil
}

func (m *ProducerManager) CreateProducer(producerGroupConfig *ProducerGroupConfig) (*EventMeshProducer, error) {
	val, ok := m.EventMeshProducers.Load(producerGroupConfig.GroupName)
	if ok {
		return val.(*EventMeshProducer), nil
	}
	pg, err := NewEventMeshProducer(producerGroupConfig)
	if err != nil {
		return nil, err
	}
	m.EventMeshProducers.Store(producerGroupConfig.GroupName, pg)
	return pg, nil
}

func (m *ProducerManager) Start() error {
	log.Infof("start producer manager")
	return nil
}

func (m *ProducerManager) Shutdown() error {
	log.Infof("shutdown producer manager")

	m.EventMeshProducers.Range(func(key, value any) bool {
		pg := value.(*EventMeshProducer)
		if err := pg.Shutdown(); err != nil {
			log.Infof("shutdown eventmesh producer:%v, err:%v", key, err)
		}
		return true
	})
	return nil
}
