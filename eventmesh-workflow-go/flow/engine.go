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

package flow

import (
	"context"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/config"
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/internal/constants"
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/internal/dal"
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/internal/dal/model"
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/internal/queue"
	"github.com/google/uuid"
)

type Engine struct {
	workflowDAL dal.WorkflowDAL
	queue       queue.ObserveQueue
}

func NewEngine() *Engine {
	var engine Engine
	engine.workflowDAL = dal.NewWorkflowDAL()
	engine.queue = queue.GetQueue(config.GlobalConfig().Flow.Queue.Store)
	return &engine
}

// Validate 验证流程执行合法性
func (e *Engine) Validate(ctx context.Context, instanceID string) error {
	return nil
}

// Start ...
func (e *Engine) Start(ctx context.Context, param *WorkflowParam) (string, error) {
	r, err := e.workflowDAL.SelectStartTask(ctx, model.WorkflowTask{WorkflowID: param.ID})
	if err != nil {
		return "", err
	}
	if r == nil {
		return "", ErrWorkflowNotExists
	}
	var workflowInstanceID = uuid.New().String()
	if err = e.workflowDAL.InsertInstance(ctx, &model.WorkflowInstance{
		WorkflowID: param.ID, WorkflowInstanceID: workflowInstanceID,
		WorkflowStatus: constants.WorkflowInstanceProcessStatus,
	}); err != nil {
		return "", err
	}
	var w = model.WorkflowTaskInstance{WorkflowInstanceID: workflowInstanceID, WorkflowID: param.ID,
		TaskID: r.TaskID, TaskInstanceId: uuid.New().String(), Status: constants.TaskInstanceWaitStatus,
		Input: param.Input}
	return workflowInstanceID, e.queue.Publish([]*model.WorkflowTaskInstance{&w})
}

// Transition ...
func (e *Engine) Transition(ctx context.Context, param *WorkflowParam) error {
	r, err := e.workflowDAL.SelectTransitionTask(ctx, model.WorkflowTask{WorkflowID: param.ID,
		WorkflowInstanceID: param.InstanceID})
	if err != nil {
		return err
	}
	var taskInstances []*model.WorkflowTaskInstance
	for _, task := range r {
		var taskInstance = model.WorkflowTaskInstance{WorkflowInstanceID: param.InstanceID, WorkflowID: param.ID,
			TaskID: task.TaskID, TaskInstanceId: uuid.New().String(), Status: constants.TaskInstanceWaitStatus,
			Input: param.Input}
		taskInstances = append(taskInstances, &taskInstance)
	}
	return e.queue.Publish(taskInstances)
}
