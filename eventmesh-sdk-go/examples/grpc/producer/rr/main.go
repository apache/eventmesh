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
	"context"
	"fmt"
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/grpc"
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/grpc/conf"
	"time"
)

func main() {
	cfg := &conf.GRPCConfig{
		Host:         "127.0.0.1",
		Port:         10205,
		ENV:          "go-grpc-test-env",
		Region:       "sh",
		IDC:          "pd",
		SYS:          "grpc-go",
		Username:     "grpc-go-username",
		Password:     "grpc-go-passwd",
		ProtocolType: grpc.EventmeshMessage,
		ProducerConfig: conf.ProducerConfig{
			ProducerGroup: "test-rr-group",
		},
		ConsumerConfig: conf.ConsumerConfig{
			Enabled: false,
		},
	}
	cli, err := grpc.New(cfg)
	if err != nil {
		panic(err)
	}
	defer func() {
		if err := cli.Close(); err != nil {
			panic(err)
		}
	}()
	builder := grpc.NewMessageBuilder()
	builder.WithHeader(grpc.CreateHeader(cfg)).
		WithContent("test for rr go grpc").
		WithProperties(map[string]string{
			"from": "grpc",
			"for":  "test"}).
		WithProducerGroup("grpc-rr-producergroup").
		WithTag("grpc rr tag").
		WithTopic("grpc-topic").
		WithTTL(time.Hour).
		WithSeqNO("1").
		WithUniqueID("1")

	msg, err := cli.RequestReply(context.TODO(), builder.SimpleMessage)
	if err != nil {
		fmt.Println("send rr msg err:" + err.Error())
		return
	}
	fmt.Println(msg.String())

}
