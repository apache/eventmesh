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

package api

import (
	"context"
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/flow"
	"github.com/gogf/gf/util/gconv"

	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/api/proto"
)

type workflowImpl struct {
	proto.UnimplementedWorkflowServer
	engine *flow.Engine
}

func NewWorkflowService() proto.WorkflowServer {
	var w workflowImpl
	w.engine = flow.NewEngine()
	return &w
}

func (s *workflowImpl) Execute(ctx context.Context, req *proto.ExecuteRequest) (*proto.ExecuteResponse, error) {
	var rsp proto.ExecuteResponse
	var param flow.WorkflowParam
	if err := gconv.Struct(req, &param); err != nil {
		return nil, err
	}
	if len(req.InstanceId) != 0 {
		return &rsp, s.engine.Transition(ctx, &param)
	}
	r, err := s.engine.Start(ctx, &param)
	if err != nil {
		return nil, err
	}
	rsp.InstanceId = r
	return &rsp, nil
}
