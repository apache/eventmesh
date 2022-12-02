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
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/flow"
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/internal/constants"
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/internal/dal/model"
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/internal/metrics"
)

type eventTask struct {
	baseTask
	operationTask Task
	action        *model.WorkflowTaskAction
	transition    *model.WorkflowTaskRelation
	flowEngine    *flow.Engine
}

func NewEventTask(instance *model.WorkflowTaskInstance) Task {
	var t eventTask
	if instance == nil || instance.Task == nil {
		return nil
	}
	t.baseTask = baseTask{taskID: instance.TaskID, taskInstanceID: instance.TaskInstanceID, input: instance.Input,
		workflowID: instance.WorkflowID, workflowInstanceID: instance.WorkflowInstanceID, taskType: instance.Task.TaskType}
	t.action = instance.Task.Actions[0]
	t.transition = instance.Task.ChildTasks[0]
	t.operationTask = NewOperationTask(instance)
	t.flowEngine = flow.NewEngine()
	return &t
}

func (t *eventTask) Run() error {
	metrics.Inc(constants.MetricsEventTask, constants.MetricsTotal)
	if t.transition == nil {
		return nil
	}
	// TODO event ref validate
	return t.operationTask.Run()
}
