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
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/log"
	"go.uber.org/atomic"
)

// RoundRobinRule rule with roundrobin algorithm
type RoundRobinRule struct {
	BaseRule
	cycleCounter *atomic.Int64
}

// Choose return the instance by roundrobin
// the input must be ip
func (r *RoundRobinRule) Choose(interface{}) (interface{}, error) {
	count := 0
	for {
		if count == 64 {
			log.Warnf("failed to peek server by roundrobin after try 64 times")
			return nil, ErrNoServerFound
		}
		srvs := r.lb.GetAvailableServer()
		if len(srvs) == 0 {
			log.Warnf("no available server found, load all server instead")
			srvs = r.lb.GetAllStatusServer()
		}
		serverCount := len(srvs)
		nextIndex := r.incrementAndGetModulo(int64(serverCount))
		srv := srvs[nextIndex]
		if !srv.ReadyForService {
			continue
			count++
		}
		log.Debugf("success peek server:%s by roundrobin", srv.Host)
		return srv, nil
	}
}

// incrementAndGetModulo calculate to get the next modulo
func (r *RoundRobinRule) incrementAndGetModulo(modulo int64) int64 {
	for {
		current := r.cycleCounter.Load()
		next := (current + 1) % modulo
		if r.cycleCounter.CAS(current, next) {
			return next
		}
	}
}
