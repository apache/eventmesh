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

package protocol

import (
	"context"
	pgrpc "github.com/apache/incubator-eventmesh/eventmesh-sdk-go/grpc"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/log"
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/internal/constants"
)

var messageBuilder = make(map[string]Message)

// Message workflow message definition
type Message interface {
	Publish(ctx context.Context, string, content string, properties map[string]string) error
}

func closeEventMeshClient(client pgrpc.Interface) {
	if client != nil {
		if err := client.Close(); err != nil {
			log.Get(constants.LogSchedule).Errorf("close eventmesh client error:%v", err)
		}
	}
}

func Builder(protocol string) Message {
	return messageBuilder[protocol]
}
