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

package utils

import (
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/log"

	"net"
	"sync"
)

var (
	// hostIP hold the client ip for current machine
	hostIPv4 string

	// ipOnce once to set the hostIP
	ipOnce sync.Once
)

// HostIPV4 return the current client ip v4
func HostIPV4() string {
	ipOnce.Do(func() {
		addrs, err := net.InterfaceAddrs()
		if err != nil {
			log.Panicf("get client ipv4, enum addrs err:%v", err)
		}
		for _, addr := range addrs {
			if ipnet, ok := addr.(*net.IPNet); ok && !ipnet.IP.IsLoopback() {
				if ipnet.IP.To4() != nil {
					hostIPv4 = ipnet.IP.String()
				}
			}
		}
		if hostIPv4 == "" {
			log.Panicf("get client ipv4 is empty")
		}
	})
	return hostIPv4
}
