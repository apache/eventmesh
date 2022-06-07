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
	"math/rand"
)

// RandomRule random rule by math.Random
type RandomRule struct {
	BaseRule
}

// Choose return the instance by random
// the input must be ip
func (r *RandomRule) Choose(interface{}) (interface{}, error) {
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
		rdi := rand.Int63n(int64(serverCount))
		srv := srvs[rdi]
		if !srv.ReadyForService {
			count++
			continue
		}

		log.Debugf("success peek server:%s by random", srv.Host)
		return srv, nil
	}
}
