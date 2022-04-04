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

package conf

import "time"

// GRPCConfig grpc configuration
type GRPCConfig struct {
	// Address about the target eventmesh server
	Address []string
	// Port port for eventmesh server
	Port int
	// HeartbeatPeriod duration to send heartbeat
	// default to 5s
	HeartbeatPeriod time.Duration

	// ConsumerConfig if the client is listen some event
	// optional
	ConsumerConfig
}

// ConsumerConfig concumer configuration, include subscribe configurations
type ConsumerConfig struct {
	Items []SubscribeItem
}

// SubscribeItem content about subscribe
type SubscribeItem struct {
	// Topic uniq for eventmesh
	Topic string
	// SubscribeType type for subscribe, support as fellow
	// ASYNC = 0;
	// SYNC = 1;
	SubscribeType int
	// SubscribeMode mode for subscribe, support as fellow
	// CLUSTERING = 0;
	// BROADCASTING = 1;
	SubscribeMode int
}
