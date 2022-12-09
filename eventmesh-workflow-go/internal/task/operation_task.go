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

package task

import (
	"context"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/config"
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/internal/constants"
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/internal/dal"
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/internal/dal/model"
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/internal/metrics"
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/internal/queue"
	"github.com/google/uuid"
)

type operationTask struct {
	baseTask
	action     *model.WorkflowTaskAction
	transition *model.WorkflowTaskRelation
}

func NewOperationTask(instance *model.WorkflowTaskInstance) Task {
	var t operationTask
	if instance == nil || instance.Task == nil {
		return nil
	}
	t.baseTask = baseTask{taskID: instance.TaskID, taskInstanceID: instance.TaskInstanceID, input: instance.Input,
		workflowID: instance.WorkflowID, workflowInstanceID: instance.WorkflowInstanceID, taskType: instance.Task.TaskType}
	t.action = instance.Task.Actions[0]
	t.transition = instance.Task.ChildTasks[0]
	t.baseTask.queue = queue.GetQueue(config.GlobalConfig().Flow.Queue.Store)
	t.workflowDAL = dal.NewWorkflowDAL()
	return &t
}

func (t *operationTask) Run() error {
	metrics.Inc(constants.MetricsOperationTask, constants.MetricsTotal)
	if t.action == nil {
		return nil
	}
	// match end
	if t.transition.ToTaskID == constants.TaskEndID {
		if t.action != nil {
			if err := publishEvent(t.workflowInstanceID, uuid.New().String(), t.action.OperationName, t.input); err != nil {
				return err
			}
		}
		return t.workflowDAL.UpdateInstance(context.Background(),
			&model.WorkflowInstance{WorkflowInstanceID: t.workflowInstanceID,
				WorkflowStatus: constants.WorkflowInstanceSuccessStatus})
	}
	var taskInstanceID = uuid.New().String()
	var taskInstance = model.WorkflowTaskInstance{WorkflowInstanceID: t.workflowInstanceID, WorkflowID: t.workflowID,
		TaskID: t.transition.ToTaskID, TaskInstanceID: taskInstanceID, Status: constants.TaskInstanceSleepStatus,
		Input: t.baseTask.input}
	if err := t.baseTask.queue.Publish([]*model.WorkflowTaskInstance{&taskInstance}); err != nil {
		return err
	}
	return publishEvent(t.workflowInstanceID, taskInstanceID, t.action.OperationName, t.input)
}
