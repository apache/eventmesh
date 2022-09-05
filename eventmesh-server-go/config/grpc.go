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

package config

// GRPCOption configuratin for grpc server
type GRPCOption struct {
	Port string `yaml:"port" toml:"port"`

	// TLSOption process with the tls configuration
	*TLSOption `yaml:"tls" toml:"tls"`

	// PProfOption if pprof is enabled, server
	// will start on given port, and you can check
	// on http://ip:port/pprof/debug
	*PProfOption `yaml:"pprof" toml:"pprof"`

	// SubscribePoolSize pool in handle subscribe msg
	// default to 10
	SubscribePoolSize int `yaml:"subscribe-pool-size" toml:"subscribe-pool-size"`
	// RetryPoolSize pool in handle retry msg
	// default to 10
	RetryPoolSize int `yaml:"retry-pool-size" toml:"retry-pool-size"`
	// PushMessagePoolSize pool to push message
	// default to 10
	PushMessagePoolSize int `yaml:"push-message-pool-size" toml:"push-message-pool-size"`

	//MsgReqNumPerSecond
	MsgReqNumPerSecond float64 `yaml:"msg-req-num-per-second" toml:"msg-req-num-per-second"`

	// RegistryName name for registry plugin support nacos or etcd
	RegistryName string `yaml:"registry-name" toml:"registry-name"`
}
