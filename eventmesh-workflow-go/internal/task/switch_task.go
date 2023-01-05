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
	"encoding/json"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/config"
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/internal/constants"
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/internal/dal"
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/internal/dal/model"
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/internal/metrics"
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/internal/queue"
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/third_party/jqer"
	"github.com/gogf/gf/util/gconv"
	"github.com/google/uuid"
	"strconv"
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
	t.baseTask = baseTask{taskID: instance.TaskID, input: instance.Input,
		workflowID: instance.WorkflowID, workflowInstanceID: instance.WorkflowInstanceID,
		taskType: instance.Task.TaskType}
	t.transitions = instance.Task.ChildTasks
	t.baseTask.queue = queue.GetQueue(config.GlobalConfig().Flow.Queue.Store)
	t.workflowDAL = dal.NewWorkflowDAL()
	t.jq = jqer.NewJQ()
	return &t
}

func (t *switchTask) Run() error {
	metrics.Inc(constants.MetricsSwitchTask, constants.MetricsTotal)
	if len(t.transitions) == 0 {
		return nil
	}
	for _, transition := range t.transitions {
		if transition.ToTaskID == constants.TaskEndID {
			continue
		}
		var jqData interface{}
		err := json.Unmarshal([]byte(t.input), &jqData)
		if err != nil {
			return err
		}
		res, err := t.jq.One(jqData, transition.Condition)
		if err != nil {
			return err
		}
		boolValue, err := strconv.ParseBool(gconv.String(res))
		if err != nil {
			return err
		}
		if !boolValue {
			metrics.Inc(constants.MetricsSwitchTask, constants.MetricsSwitchReject)
			continue
		}

		metrics.Inc(constants.MetricsSwitchTask, constants.MetricsSwitchPass)
		var taskInstance = model.WorkflowTaskInstance{WorkflowInstanceID: t.workflowInstanceID,
			WorkflowID: t.workflowID, TaskID: transition.ToTaskID, TaskInstanceID: uuid.New().String(),
			Status: constants.TaskInstanceWaitStatus, Input: t.baseTask.input}
		return t.baseTask.queue.Publish([]*model.WorkflowTaskInstance{&taskInstance})
	}
	// not match
	return t.workflowDAL.UpdateInstance(context.Background(),
		&model.WorkflowInstance{WorkflowInstanceID: t.workflowInstanceID,
			WorkflowStatus: constants.WorkflowInstanceSuccessStatus})
}
