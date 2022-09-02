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

package dal

import (
	"context"
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/internal/util"
	"time"

	"github.com/apache/incubator-eventmesh/eventmesh-server-go/log"
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/internal/constants"
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/internal/dal/model"
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/third_party/swf"
	"github.com/gogf/gf/util/gconv"
	"github.com/google/uuid"
	pmodel "github.com/serverlessworkflow/sdk-go/v2/model"
	"gorm.io/gorm"
)

type WorkflowDAL interface {
	Select(ctx context.Context, workflowID string) (*model.Workflow, error)
	SelectStartTask(ctx context.Context, condition model.WorkflowTask) (*model.WorkflowTask, error)
	SelectTransitionTask(ctx context.Context, condition model.WorkflowTask) ([]*model.WorkflowTask, error)
	SelectChildTask(ctx context.Context, condition model.WorkflowTask) ([]*model.WorkflowTask, error)
	SelectTaskInstance(ctx context.Context, condition model.WorkflowTaskInstance) (*model.WorkflowTaskInstance, error)
	Insert(ctx context.Context, record *model.Workflow) error
	InsertInstance(ctx context.Context, record *model.WorkflowInstance) error
	InsertTaskInstance(ctx context.Context, record *model.WorkflowTaskInstance) error
	UpdateTaskInstance(ctx context.Context, record *model.WorkflowTaskInstance) error
}

func NewWorkflowDAL() WorkflowDAL {
	var w workflowDALImpl
	return &w
}

type workflowDALImpl struct {
}

func (w *workflowDALImpl) Select(ctx context.Context, workflowID string) (*model.Workflow, error) {
	var condition = model.Workflow{WorkflowID: workflowID}
	var r model.Workflow
	if err := workflowDB.WithContext(ctx).Where(&condition).First(&r).Error; err != nil {
		if err == gorm.ErrRecordNotFound {
			return nil, nil
		}
		return nil, err
	}
	return &r, nil
}

func (w *workflowDALImpl) SelectStartTask(ctx context.Context, condition model.WorkflowTask) (*model.WorkflowTask,
	error) {
	var c = model.WorkflowTaskRelation{FromTaskID: constants.TaskStartID, WorkflowID: condition.WorkflowID,
		Status: constants.TaskNormalStatus}
	var r model.WorkflowTaskRelation
	if err := workflowDB.WithContext(ctx).Where(&c).First(&r).Error; err != nil {
		if err == gorm.ErrRecordNotFound {
			return nil, nil
		}
		return nil, err
	}

	return &model.WorkflowTask{TaskID: r.ToTaskID, WorkflowID: condition.WorkflowID}, nil
}

func (w *workflowDALImpl) SelectTransitionTask(ctx context.Context, condition model.WorkflowTask) (
	[]*model.WorkflowTask, error) {
	var c = model.WorkflowTaskInstance{WorkflowInstanceID: condition.WorkflowInstanceID,
		Status: constants.TaskInstanceSuccessStatus}
	var r model.WorkflowTaskInstance
	if err := workflowDB.WithContext(ctx).Where(&c).Order("update_time DESC").
		First(&r).Error; err != nil {
		if err == gorm.ErrRecordNotFound {
			return nil, nil
		}
		return nil, err
	}
	return w.SelectChildTask(ctx, model.WorkflowTask{WorkflowID: condition.WorkflowID, TaskID: r.TaskID})
}

func (w *workflowDALImpl) SelectChildTask(ctx context.Context, condition model.WorkflowTask) (
	[]*model.WorkflowTask, error) {
	var c = model.WorkflowTaskRelation{FromTaskID: condition.TaskID, WorkflowID: condition.WorkflowID,
		Status: constants.TaskNormalStatus}
	var rel []*model.WorkflowTaskRelation
	if err := workflowDB.WithContext(ctx).Where(&c).Find(&rel).Error; err != nil {
		return nil, err
	}
	if len(rel) == 0 {
		return nil, nil
	}
	var taskIDs []string
	for _, r := range rel {
		taskIDs = append(taskIDs, r.ToTaskID)
	}

	var handlers []func() error
	var err error
	var tasks []*model.WorkflowTask
	var taskActions []*model.WorkflowTaskAction
	handlers = append(handlers, func() error {
		tasks, err = w.selectTask(context.Background(), condition.WorkflowID, taskIDs)
		if err != nil {
			return err
		}
		return nil
	})
	handlers = append(handlers, func() error {
		taskActions, err = w.selectTaskAction(context.Background(), condition.WorkflowID, taskIDs)
		if err != nil {
			return err
		}
		return nil
	})
	if err := util.GoAndWait(handlers...); err != nil {
		return nil, err
	}
	return w.completeTask(tasks, taskActions), nil
}

func (w *workflowDALImpl) SelectTaskInstance(ctx context.Context, condition model.WorkflowTaskInstance) (*model.
	WorkflowTaskInstance, error) {
	var r model.WorkflowTaskInstance
	if err := workflowDB.WithContext(ctx).Where(&condition).First(&r).Error; err != nil {
		if err == gorm.ErrRecordNotFound {
			return nil, nil
		}
		return nil, err
	}
	return &r, nil
}

func (w *workflowDALImpl) Insert(ctx context.Context, record *model.Workflow) error {
	return workflowDB.WithContext(ctx).Transaction(func(tx *gorm.DB) error {
		wf, err := swf.Parse(record.Definition)
		if err != nil {
			return err
		}
		var handlers []func() error
		handlers = append(handlers, func() error {
			return tx.Create(record).Error
		})
		tasks := w.buildTask(wf)
		for _, task := range tasks {
			task := task
			handlers = append(handlers, func() error {
				return tx.Create(task).Error
			})
			for _, action := range task.Actions {
				action := action
				handlers = append(handlers, func() error {
					return tx.Create(action).Error
				})
			}
		}
		taskRelations := w.buildTaskRelation(wf, tasks)
		for _, relation := range taskRelations {
			relation := relation
			handlers = append(handlers, func() error {
				return tx.Create(relation).Error
			})
		}
		return util.GoAndWait(handlers...)
	})
}

func (w *workflowDALImpl) InsertInstance(ctx context.Context, record *model.WorkflowInstance) error {
	record.CreateTime = time.Now()
	record.UpdateTime = time.Now()
	return workflowDB.WithContext(ctx).Create(&record).Error
}

func (w *workflowDALImpl) InsertTaskInstance(ctx context.Context,
	record *model.WorkflowTaskInstance) error {
	record.CreateTime = time.Now()
	record.UpdateTime = time.Now()
	return workflowDB.WithContext(ctx).Create(&record).Error
}

func (w *workflowDALImpl) UpdateTaskInstance(ctx context.Context, record *model.WorkflowTaskInstance) error {
	var condition = model.WorkflowInstance{ID: record.ID}
	return workflowDB.WithContext(ctx).Where(&condition).Updates(&record).Error
}

func (w *workflowDALImpl) buildTask(workflow *pmodel.Workflow) []*model.WorkflowTask {
	if workflow == nil || len(workflow.States) == 0 {
		return nil
	}
	var tasks []*model.WorkflowTask

	for _, state := range workflow.States {
		var task = model.WorkflowTask{}
		task.WorkflowID = workflow.ID
		task.TaskID = uuid.New().String()
		task.TaskName = state.GetName()
		task.Status = constants.TaskNormalStatus
		task.TaskType = gconv.String(state.GetType())
		task.CreateTime = time.Now()
		task.UpdateTime = time.Now()
		task.Actions = w.buildTaskAction(task.TaskID, workflow, state)
		tasks = append(tasks, &task)
	}
	return tasks
}

func (w *workflowDALImpl) buildTaskAction(taskID string, workflow *pmodel.Workflow,
	state pmodel.State) []*model.WorkflowTaskAction {
	var functions = make(map[string]*pmodel.Function)
	for i, function := range workflow.Functions {
		functions[function.Name] = &workflow.Functions[i]
	}
	switch state.GetType() {
	case pmodel.StateTypeOperation:
		return w.doBuildOperationTaskAction(workflow.ID, taskID, functions, state)
	case pmodel.StateTypeEvent:
		return w.doBuildEventTaskAction(workflow.ID, taskID, functions, state)
	}
	return nil
}

func (w *workflowDALImpl) buildTaskRelation(workflow *pmodel.Workflow,
	tasks []*model.WorkflowTask) []*model.WorkflowTaskRelation {
	if workflow == nil || len(workflow.States) == 0 {
		return nil
	}
	var taskIDs = make(map[string]string)
	for _, task := range tasks {
		taskIDs[task.TaskName] = task.TaskID
	}
	var taskRelations []*model.WorkflowTaskRelation
	for _, state := range workflow.States {
		if workflow.Start.StateName == state.GetName() {
			taskRelations = append(taskRelations, w.doBuildStartTaskRelation(workflow, state, taskIDs))
		}
		switch state.GetType() {
		case pmodel.StateTypeOperation:
		case pmodel.StateTypeEvent:
			taskRelations = append(taskRelations, w.doBuildTaskRelation(workflow, state, taskIDs))
		case pmodel.StateTypeSwitch:
			taskRelations = append(taskRelations, w.doBuildSwitchTaskRelation(workflow, state, taskIDs)...)
		default:
			log.Errorf("buildTaskRelation=not support type=%s", state.GetType())
		}
	}
	return taskRelations
}

func (w *workflowDALImpl) doBuildOperationTaskAction(workflowID string, taskID string,
	functions map[string]*pmodel.Function, state pmodel.State) []*model.WorkflowTaskAction {
	s, ok := state.(*pmodel.OperationState)
	if !ok {
		return nil
	}
	var actions []*model.WorkflowTaskAction
	for _, action := range s.Actions {
		var taskAction model.WorkflowTaskAction
		taskAction.WorkflowID = workflowID
		taskAction.TaskID = taskID
		function := functions[action.FunctionRef.RefName]
		if function == nil {
			continue
		}
		taskAction.OperationName = gconv.String(function.Operation)
		taskAction.OperationType = gconv.String(function.Type)
		taskAction.Status = constants.TaskNormalStatus
		taskAction.CreateTime = time.Now()
		taskAction.UpdateTime = time.Now()
		actions = append(actions, &taskAction)
	}
	return actions
}

func (w *workflowDALImpl) doBuildEventTaskAction(workflowID string, taskID string,
	functions map[string]*pmodel.Function, state pmodel.State) []*model.WorkflowTaskAction {
	s, ok := state.(*pmodel.EventState)
	if !ok {
		return nil
	}
	var actions []*model.WorkflowTaskAction
	for _, event := range s.OnEvents {
		for _, action := range event.Actions {
			var taskAction model.WorkflowTaskAction
			taskAction.WorkflowID = workflowID
			taskAction.TaskID = taskID
			function := functions[action.FunctionRef.RefName]
			if function == nil {
				continue
			}
			taskAction.OperationName = gconv.String(function.Operation)
			taskAction.OperationType = gconv.String(function.Type)
			taskAction.Status = constants.TaskNormalStatus
			taskAction.CreateTime = time.Now()
			taskAction.UpdateTime = time.Now()
			actions = append(actions, &taskAction)
		}
	}
	return actions
}

func (w *workflowDALImpl) doBuildTaskRelation(workflow *pmodel.Workflow, state pmodel.State,
	taskIDs map[string]string) *model.WorkflowTaskRelation {
	var r = model.WorkflowTaskRelation{}
	r.WorkflowID = workflow.ID
	r.FromTaskID = taskIDs[state.GetName()]
	if state.GetTransition() == nil && !state.GetEnd().Terminate {
		r.ToTaskID = constants.TaskEndID
	} else {
		r.ToTaskID = taskIDs[state.GetTransition().NextState]
	}
	r.Status = constants.TaskNormalStatus
	r.CreateTime = time.Now()
	r.UpdateTime = time.Now()
	return &r
}

func (w *workflowDALImpl) doBuildSwitchTaskRelation(workflow *pmodel.Workflow, state pmodel.State,
	taskIDs map[string]string) []*model.WorkflowTaskRelation {
	s, ok := state.(*pmodel.DataBasedSwitchState)
	if !ok {
		return nil
	}
	var rel []*model.WorkflowTaskRelation
	if !s.DefaultCondition.End.Terminate {
		var r = model.WorkflowTaskRelation{}
		r.WorkflowID = workflow.ID
		r.FromTaskID = taskIDs[state.GetName()]
		r.ToTaskID = constants.TaskEndID
		r.Status = constants.TaskNormalStatus
		r.CreateTime = time.Now()
		r.UpdateTime = time.Now()
		rel = append(rel, &r)
	}
	for _, condition := range s.DataConditions {
		var r = model.WorkflowTaskRelation{}
		r.WorkflowID = workflow.ID
		r.FromTaskID = taskIDs[state.GetName()]
		r.Status = constants.TaskNormalStatus
		r.CreateTime = time.Now()
		r.UpdateTime = time.Now()
		if c, ok := condition.(*pmodel.TransitionDataCondition); ok {
			r.ToTaskID = taskIDs[c.Transition.NextState]
			r.Condition = c.Condition
		}
		if c, ok := condition.(*pmodel.EndDataCondition); ok {
			r.ToTaskID = constants.TaskEndID
			r.Condition = c.Condition
		}
		rel = append(rel, &r)
	}
	return rel
}

func (w *workflowDALImpl) doBuildStartTaskRelation(workflow *pmodel.Workflow, state pmodel.State,
	taskIDs map[string]string) *model.WorkflowTaskRelation {
	var r = model.WorkflowTaskRelation{}
	r.WorkflowID = workflow.ID
	r.FromTaskID = constants.TaskStartID
	r.ToTaskID = taskIDs[state.GetName()]
	r.Status = constants.TaskNormalStatus
	r.CreateTime = time.Now()
	r.UpdateTime = time.Now()
	return &r
}

func (w *workflowDALImpl) selectTask(ctx context.Context, workflowID string, taskIDs []string) ([]*model.WorkflowTask,
	error) {
	if len(taskIDs) == 0 {
		return nil, nil
	}
	var condition = model.WorkflowTask{WorkflowID: workflowID, TaskIDs: taskIDs}
	var r []*model.WorkflowTask
	if err := workflowDB.WithContext(ctx).Where(&condition).Where("task_id = ?", taskIDs).
		Find(&r).Error; err != nil {
		return nil, err
	}
	return r, nil
}

func (w *workflowDALImpl) selectTaskAction(ctx context.Context,
	workflowID string, taskIDs []string) ([]*model.WorkflowTaskAction, error) {
	if len(taskIDs) == 0 {
		return nil, nil
	}
	var condition = model.WorkflowTaskAction{WorkflowID: workflowID, TaskIDs: taskIDs}
	var r []*model.WorkflowTaskAction
	if err := workflowDB.WithContext(ctx).Where(&condition).Where("task_id = ?", taskIDs).
		Find(&r).Error; err != nil {
		return nil, err
	}
	return r, nil
}

func (w *workflowDALImpl) completeTask(tasks []*model.WorkflowTask,
	taskActions []*model.WorkflowTaskAction) []*model.WorkflowTask {
	var r []*model.WorkflowTask
	var t = make(map[string][]*model.WorkflowTaskAction)
	for _, action := range taskActions {
		t[action.TaskID] = append(t[action.TaskID], action)
	}
	for _, task := range tasks {
		var wt model.WorkflowTask
		if err := gconv.Struct(task, &wt); err != nil {
			continue
		}
		wt.Actions = t[task.TaskID]
		r = append(r, &wt)
	}
	return r
}
