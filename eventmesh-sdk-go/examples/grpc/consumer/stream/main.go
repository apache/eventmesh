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

package main

import (
	"fmt"
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/grpc"
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/grpc/conf"
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/grpc/proto"
	"time"
)

func main() {
	cli, err := grpc.New(&conf.GRPCConfig{
		Host:         "101.43.84.47",
		Port:         10205,
		ENV:          "go-grpc-test-env",
		Region:       "sh",
		IDC:          "pd",
		SYS:          "grpc-go",
		Username:     "grpc-go-username",
		Password:     "grpc-go-passwd",
		ProtocolType: grpc.EventmeshMessage,
		ProducerConfig: conf.ProducerConfig{
			ProducerGroup: "test-sync-consumer-group",
		},
		ConsumerConfig: conf.ConsumerConfig{
			Enabled:       true,
			ConsumerGroup: "test-consumer-group-subscribe",
			PoolSize:      5,
		},
		HeartbeatConfig: conf.HeartbeatConfig{
			Period:  time.Second * 5,
			Timeout: time.Second * 3,
		},
	})
	if err != nil {
		panic(err)
	}
	defer func() {
		if err := cli.Close(); err != nil {
			panic(err)
		}
	}()
	err = cli.SubscribeStream(conf.SubscribeItem{
		SubscribeMode: conf.CLUSTERING,
		SubscribeType: conf.ASYNC,
		Topic:         "grpc-topic",
	}, func(msg *proto.SimpleMessage) interface{} {
		fmt.Println("receive msg: " + msg.String())
		return "nil"
	})
	if err != nil {
		fmt.Println(err.Error())
		return
	}
	time.Sleep(time.Hour)
}
