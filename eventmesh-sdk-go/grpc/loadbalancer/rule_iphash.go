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
	"github.com/cespare/xxhash"
	"reflect"
	"unsafe"
)

// IPHashRule iphash rule
type IPHashRule struct {
	BaseRule
}

// Choose return the instance by ip hash
// the input must be ip
func (i *IPHashRule) Choose(ip interface{}) (interface{}, error) {
	host, ok := ip.(string)
	if !ok {
		log.Warnf("provide host not a string type:%s", reflect.TypeOf(ip).String())
		return nil, ErrInvalidInput
	}
	srvs := i.lb.GetAvailableServer()
	if len(srvs) == 0 {
		log.Warnf("no available server found, load all server instead")
		srvs = i.lb.GetAllStatusServer()
	}
	count := len(srvs)
	hashN := xxhash.Sum64(*(*[]byte)(unsafe.Pointer(&host))) % uint64(count)
	srv := srvs[hashN]
	log.Debugf("success peek host:%s by iphash, input:%v", srv.Host, ip)
	return srv, nil
}
