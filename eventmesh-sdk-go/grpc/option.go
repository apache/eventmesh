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
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/common/id"
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/common/seq"
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/log"
)

// GRPCOption option to set up the option for grpc client
type GRPCOption func(*eventMeshGRPCClient)

// WithLogger set the logger for client, replace with the default
func WithLogger(l log.Logger) GRPCOption {
	return func(client *eventMeshGRPCClient) {
		log.SetLogger(l)
	}
}

// WithIDG setup the id generate api
func WithIDG(i id.Interface) GRPCOption {
	return func(client *eventMeshGRPCClient) {
		client.idg = i
	}
}

func WithSeq(i seq.Interface) GRPCOption {
	return func(client *eventMeshGRPCClient) {
		client.seqg = i
	}
}
