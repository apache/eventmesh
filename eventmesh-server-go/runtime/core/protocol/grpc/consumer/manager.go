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

package consumer

import (
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/log"
	"sync"
)

type Manager struct {
	// consumerClients store all consumer clients
	// key is consumer group, value is []*GroupClient
	consumerGroupClients *sync.Map

	// consumers eventmesh consumer instances
	// key is consumer group, value is EventMeshConsumer
	consumers *sync.Map
}

// NewManager create new consumer manager
func NewManager() (*Manager, error) {
	return &Manager{
		consumers:            new(sync.Map),
		consumerGroupClients: new(sync.Map),
	}, nil
}

func (c *Manager) GetConsumer(consumerGroup string) (*EventMeshConsumer, error) {
	val, ok := c.consumers.Load(consumerGroup)
	if ok {
		return val.(*EventMeshConsumer), nil
	}
	cu, err := NewEventMeshConsumer(consumerGroup)
	if err != nil {
		return nil, err
	}
	c.consumers.Store(consumerGroup, cu)
	return cu, nil
}

func (c *Manager) Start() error {
	log.Infof("start consumer manager")
	return nil
}

func (c *Manager) Stop() error {
	return nil
}
