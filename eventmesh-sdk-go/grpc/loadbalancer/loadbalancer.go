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

package loadbalancer

import (
	"fmt"
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/grpc/conf"
	"go.uber.org/atomic"
	"sync"
)

// LoadBalancer interface to process all grpc client
type LoadBalancer interface {
	// GetAllStatusServer return all status server in the lb
	GetAllStatusServer() []*StatusServer

	// GetAvailableServer available servers which is ready for service
	GetAvailableServer() []*StatusServer

	// AddServer add servers to LoadBalancer
	AddServer([]*StatusServer)

	// Choose peek client with rule inside
	Choose(input interface{}) (interface{}, error)
}

// BaseLoadBalancer loadbalancer instance
type BaseLoadBalancer struct {
	servers []*StatusServer
	lock    *sync.RWMutex
	rule    Rule
}

// NewLoadBalancer create new loadbalancer with lb type and servers
func NewLoadBalancer(lbType conf.LoadBalancerType, srvs []*StatusServer) (LoadBalancer, error) {
	lb := &BaseLoadBalancer{
		servers: srvs,
		lock:    new(sync.RWMutex),
	}
	switch lbType {
	case conf.IPHash:
		lb.rule = &IPHashRule{BaseRule{
			lb: lb,
		}}
	case conf.Random:
		lb.rule = &RandomRule{BaseRule{
			lb: lb,
		}}
	case conf.RoundRobin:
		lb.rule = &RoundRobinRule{
			cycleCounter: atomic.NewInt64(-1),
			BaseRule:     BaseRule{lb: lb},
		}
	default:
		return nil, fmt.Errorf("invalid load balancer rule:%v", lbType)
	}

	return lb, nil
}

// AddServer add servers to the lb
func (b *BaseLoadBalancer) AddServer(srvs []*StatusServer) {
	b.lock.Lock()
	defer b.lock.Unlock()
	b.servers = append(b.servers, srvs...)
}

// Choose choose server in current lb
func (b *BaseLoadBalancer) Choose(input interface{}) (interface{}, error) {
	return b.rule.Choose(input)
}

func (b *BaseLoadBalancer) GetAllStatusServer() []*StatusServer {
	b.lock.RLock()
	defer b.lock.RUnlock()
	return b.servers
}

func (b *BaseLoadBalancer) GetAvailableServer() []*StatusServer {
	b.lock.RLock()
	defer b.lock.RUnlock()
	var asrvs []*StatusServer
	for _, r := range b.servers {
		if r.ReadyForService {
			asrvs = append(asrvs, r)
		}
	}
	return asrvs
}
