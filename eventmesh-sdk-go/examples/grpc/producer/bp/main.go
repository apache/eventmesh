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
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/grpc/proto"
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
			ProducerGroup: "test-batch-group",
		},
		ConsumerConfig: conf.ConsumerConfig{
			Enabled: false,
		},
	}
	cli, err := grpc.New(cfg)
	if err != nil {
		fmt.Println("create publish client err:" + err.Error())
		return
	}
	defer func() {
		if err := cli.Close(); err != nil {
			panic(err)
		}
	}()
	batchMsg := &proto.BatchMessage{
		Header:        grpc.CreateHeader(cfg),
		ProducerGroup: "grpc-producergroup",
		Topic:         "grpc-batch-topic",
		MessageItem: []*proto.BatchMessage_MessageItem{
			{
				Content:  "test for batch publish go grpc -1",
				Ttl:      "1024",
				UniqueId: "110",
				SeqNum:   "111",
				Tag:      "batch publish tag 1",
				Properties: map[string]string{
					"from": "grpc",
					"type": "batch publish",
				},
			},
			{
				Content:  "test for batch publish go grpc",
				Ttl:      "1024",
				UniqueId: "210",
				SeqNum:   "211",
				Tag:      "batch publish tag 2",
				Properties: map[string]string{
					"from": "grpc",
					"type": "batch publish",
				},
			},
		},
	}
	resp, err := cli.BatchPublish(context.TODO(), batchMsg)
	if err != nil {
		panic(err)
	}
	fmt.Println(resp.String())
}
