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

package service

import (
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/config"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/runtime/proto/pb"
	"github.com/panjf2000/ants/v2"
)

type Heartbeat struct {
	pb.UnimplementedHeartbeatServiceServer
	gctx *GRPCContext
	pool *ants.Pool
}

func NewHeartbeatServiceServer(gctx *GRPCContext) (*Heartbeat, error) {
	sp := config.GlobalConfig().Server.GRPCOption.SubscribePoolSize
	pl, err := ants.NewPool(sp)
	if err != nil {
		return nil, err
	}
	return &Heartbeat{
		gctx: gctx,
		pool: pl,
	}, nil
}
