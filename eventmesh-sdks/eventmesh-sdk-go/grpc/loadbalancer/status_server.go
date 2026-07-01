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

import "fmt"

// StatusServer server with status check
type StatusServer struct {
	// ReadyForService indicate the remote is connected
	// and ready for service for client
	ReadyForService bool
	// Host holds the eventmesh server host
	Host string
	// RealServer holds the grpc client, producer/consumer/heartbeat
	RealServer interface{}
}

// NewStatusServer create new status server
func NewStatusServer(in interface{}, host string) *StatusServer {
	return &StatusServer{
		RealServer:      in,
		Host:            host,
		ReadyForService: true,
	}
}

// String return the description about the server
func (s *StatusServer) String() string {
	return fmt.Sprintf("removeAddr:%s, readyForService:%v", s.Host, s.ReadyForService)
}
