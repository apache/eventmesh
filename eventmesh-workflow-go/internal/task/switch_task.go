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
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/config"
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/internal/constants"
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/internal/dal/model"
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/internal/queue"
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/third_party/jqer"
	"github.com/gogf/gf/util/gconv"
)

type switchTask struct {
	baseTask
	transitions []*model.WorkflowTaskRelation
	jq          jqer.JQ
}

func NewSwitchTask(instance *model.WorkflowTaskInstance) Task {
	var t switchTask
	if instance == nil || instance.Task == nil {
		return nil
	}
	t.baseTask = baseTask{taskID: instance.TaskID, taskInstanceID: instance.TaskInstanceID, input: instance.Input,
		workflowID: instance.WorkflowID, workflowInstanceID: instance.WorkflowInstanceID,
		taskType: instance.Task.TaskType}
	t.transitions = instance.Task.ChildTasks
	t.baseTask.queue = queue.GetQueue(config.GlobalConfig().Flow.Queue.Store)
	t.jq = jqer.NewJQ()
	return &t
}

func (t *switchTask) Run() error {
	if len(t.transitions) == 0 {
		return nil
	}
	var tasks []*model.WorkflowTaskInstance
	for _, transition := range t.transitions {
		res, err := t.jq.One(t.input, transition.Condition)
		if err != nil {
			return err
		}
		if !gconv.Bool(res) {
			continue
		}
		if transition.ToTaskID == constants.TaskEndID {
			break
		}
		var taskInstance = model.WorkflowTaskInstance{WorkflowInstanceID: t.workflowInstanceID,
			WorkflowID: t.workflowID, TaskID: transition.ToTaskID, TaskInstanceID: t.taskInstanceID,
			Status: constants.TaskInstanceWaitStatus, Input: t.baseTask.input}
		tasks = append(tasks, &taskInstance)
	}
	return t.baseTask.queue.Publish(tasks)
}
