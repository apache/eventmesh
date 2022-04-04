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

import "sync"

type LoadBalancer interface {
	// GetAllStatusServer return all status server in the lb
	GetAllStatusServer() []*StatusServer

	// GetAvailableServer available servers which is ready for service
	GetAvailableServer() []*StatusServer
}

// BaseLoadBalancer loadbalancer instance
type BaseLoadBalancer struct {
	servers []*StatusServer
	lock    *sync.RWMutex
	rule    Rule
}

// AddServer add servers to the lb
func (b *BaseLoadBalancer) AddServer() {

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
