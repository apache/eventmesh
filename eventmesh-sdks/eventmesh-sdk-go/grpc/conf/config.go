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

import (
	"fmt"
	"time"
)

var (
	// Language define the current sdk language
	Language = "GO"
	// ProtocolDesc the protocol type
	ProtocolDesc = "grpc"

	// ProtocolVersion version for current sdk used
	ProtocolVersion = "1.0"
)

// GRPCConfig grpc configuration
type GRPCConfig struct {
	// Hosts about the target eventmesh server
	Host string `validator:"required"`
	// Port port for eventmesh server
	Port int `validator:"required"`
	// ENV environment for client
	ENV string
	// Region always be the location
	Region string
	// IDC idc district
	IDC string
	// SYS system name
	SYS string
	// Username to access the eventmesh
	Username string
	// Password to access the eventmesh
	Password string
	// ProtocolType the type for current protocol
	ProtocolType string
	// ConsumerConfig if the client is listen some event
	// optional
	ConsumerConfig

	// ProducerConfig if the client need to send message
	// you should configure it
	// optional
	ProducerConfig

	// HeartbeatConfig heartbeat configuration
	HeartbeatConfig
}

// LoadBalancerType type for LoadBalancer
type LoadBalancerType string

var (
	Random     LoadBalancerType = "random"
	RoundRobin LoadBalancerType = "roundrobin"
	IPHash     LoadBalancerType = "iphash"
)

// ProducerConfig configuration producer
type ProducerConfig struct {
	// LoadBalancerType load balancer type, support random/roundrobin/iphash
	LoadBalancerType LoadBalancerType
	// ProducerGroup uniq consumer group for current client
	ProducerGroup string
}

// HeartbeatConfig heartbeat configuration
// required
type HeartbeatConfig struct {
	// Period duration to send heartbeat
	// default to 5s
	Period time.Duration
	// Timeout ticker in send heartbeat msg
	// default to 3s
	Timeout time.Duration
}

// ConsumerConfig consumer configuration, include subscribe configurations
type ConsumerConfig struct {
	// Enabled enable subscribe
	Enabled bool
	// ConsumerGroup uniq consumergroup for current client
	ConsumerGroup string
	// PoolSize goroutine pool to dispatch msg for a topic
	PoolSize int
	// Timeout in handle received msg
	// default to 5s
	Timeout time.Duration
}

type SubscriptionMode int

const (
	CLUSTERING   SubscriptionMode = 0
	BROADCASTING SubscriptionMode = 1
)

type SubscriptionType int

const (
	ASYNC = 0
	SYNC  = 1
)

// SubscribeItem content about subscribe
type SubscribeItem struct {
	// Topic uniq for eventmesh
	Topic string
	// SubscribeType type for subscribe, support as fellow
	SubscribeType SubscriptionType
	// SubscribeMode mode for subscribe, support as fellow
	SubscribeMode SubscriptionMode
}

// ValidateDefaultConf set the default configuration if user not provided
// check the conf which is required, and return not nil with parameter error
func ValidateDefaultConf(cfg *GRPCConfig) error {
	if len(cfg.Host) == 0 {
		return fmt.Errorf("no host provided")
	}
	if cfg.ConsumerConfig.Enabled {
		if cfg.ConsumerConfig.ConsumerGroup == "" {
			return fmt.Errorf("consumer enabled, but consumer group is empty")
		}
	}
	if cfg.HeartbeatConfig.Timeout == 0 {
		cfg.HeartbeatConfig.Timeout = time.Second * 3
	}
	if cfg.HeartbeatConfig.Period == 0 {
		cfg.HeartbeatConfig.Period = time.Second * 5
	}
	if cfg.ConsumerConfig.PoolSize == 0 {
		cfg.ConsumerConfig.PoolSize = 5
	}
	if cfg.ConsumerConfig.Timeout == 0 {
		cfg.ConsumerConfig.Timeout = time.Second * 5
	}
	return nil
}
