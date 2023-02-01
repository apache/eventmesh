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
	"errors"
	"fmt"
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

const maxSize = 100

type WorkflowDAL interface {
	Select(ctx context.Context, tx *gorm.DB, workflowID string) (*model.Workflow, error)
	SelectList(ctx context.Context, param *model.QueryParam) ([]model.Workflow, int, error)
	Save(ctx context.Context, record *model.Workflow) error
	Delete(ctx context.Context, workflowID string) error
	SelectInstances(ctx context.Context, param *model.QueryParam) ([]model.WorkflowInstance, int, error)
	SelectStartTask(ctx context.Context, condition model.WorkflowTask) (*model.WorkflowTask, error)
	SelectTransitionTask(ctx context.Context, condition model.WorkflowTaskInstance) (*model.WorkflowTaskInstance, error)
	SelectTaskInstance(ctx context.Context, condition model.WorkflowTaskInstance) (*model.WorkflowTaskInstance, error)
	InsertInstance(ctx context.Context, record *model.WorkflowInstance) error
	InsertTaskInstance(ctx context.Context, record *model.WorkflowTaskInstance) error
	UpdateInstance(ctx context.Context, record *model.WorkflowInstance) error
	UpdateTaskInstance(tx *gorm.DB, record *model.WorkflowTaskInstance) error
}

func NewWorkflowDAL() WorkflowDAL {
	var w workflowDALImpl
	return &w
}

type workflowDALImpl struct {
}

func (w *workflowDALImpl) Select(ctx context.Context, tx *gorm.DB, workflowID string) (*model.Workflow, error) {
	var condition = model.Workflow{WorkflowID: workflowID, Status: constants.NormalStatus}
	var r model.Workflow
	if err := tx.WithContext(ctx).Where(&condition).First(&r).Error; err != nil {
		if err == gorm.ErrRecordNotFound {
			return nil, nil
		}
		return nil, err
	}
	return &r, nil
}

func (w *workflowDALImpl) SelectList(ctx context.Context, param *model.QueryParam) ([]model.Workflow, int, error) {
	var res []model.Workflow
	var condition = model.Workflow{WorkflowID: param.WorkflowID, Status: param.Status}
	db := workflowDB.WithContext(ctx).Where("1=1")
	if len(condition.WorkflowID) > 0 {
		// Suitable for small amount of data
		// when the amount of data is too large, you need to use search engines for optimization
		db = db.Where("workflow_id LIKE ?", fmt.Sprintf("%%%s%%", condition.WorkflowID))
	}
	if condition.Status == 0 {
		condition.Status = constants.NormalStatus
	}
	db = db.Where("status = ?", condition.Status)
	if param.Size > maxSize {
		param.Size = maxSize
	}
	if param.Page == 0 {
		param.Page = 1
	}
	var count int64
	db = db.Limit(param.Size).Offset(param.Size * (param.Page - 1)).Order("update_time DESC")
	if err := db.Find(&res).Limit(-1).Offset(-1).Count(&count).Error; err != nil {
		if err == gorm.ErrRecordNotFound {
			return nil, 0, nil
		}
		return nil, 0, err
	}
	if count == 0 {
		return res, int(count), nil
	}
	if err := w.fillInstanceCount(res); err != nil {
		return res, int(count), err
	}
	return res, int(count), nil
}

func (w *workflowDALImpl) SelectInstances(ctx context.Context, param *model.QueryParam) ([]model.WorkflowInstance,
	int, error) {
	var r []model.WorkflowInstance
	db := workflowDB.WithContext(ctx).Where("workflow_status != ?", constants.InvalidStatus).
		Where("workflow_id = ?", param.WorkflowID)
	if param.Size > maxSize {
		param.Size = maxSize
	}
	if param.Page == 0 {
		param.Page = 1
	}
	var count int64
	db = db.Limit(param.Size).Offset(param.Size * (param.Page - 1)).Order("update_time DESC")
	if err := db.Find(&r).Limit(-1).Offset(-1).Count(&count).Error; err != nil {
		if err == gorm.ErrRecordNotFound {
			return nil, 0, nil
		}
		return nil, 0, err
	}
	return r, int(count), nil
}

func (w *workflowDALImpl) SelectStartTask(ctx context.Context, condition model.WorkflowTask) (*model.WorkflowTask,
	error) {
	var c = model.WorkflowTaskRelation{FromTaskID: constants.TaskStartID, WorkflowID: condition.WorkflowID,
		Status: constants.NormalStatus}
	var r model.WorkflowTaskRelation
	if err := workflowDB.WithContext(ctx).Where(&c).First(&r).Error; err != nil {
		if err == gorm.ErrRecordNotFound {
			return nil, nil
		}
		return nil, err
	}

	return &model.WorkflowTask{TaskID: r.ToTaskID, WorkflowID: condition.WorkflowID}, nil
}

func (w *workflowDALImpl) SelectTransitionTask(ctx context.Context, condition model.WorkflowTaskInstance) (
	*model.WorkflowTaskInstance, error) {
	var r model.WorkflowTaskInstance
	if err := workflowDB.WithContext(ctx).Where(&condition).First(&r).Error; err != nil {
		if err == gorm.ErrRecordNotFound {
			return nil, nil
		}
		return nil, err
	}
	return &r, nil
}

func (w *workflowDALImpl) SelectTaskInstance(ctx context.Context, condition model.WorkflowTaskInstance) (*model.
	WorkflowTaskInstance, error) {
	var r model.WorkflowTaskInstance
	if err := workflowDB.WithContext(ctx).Where(&condition).Order("create_time desc").
		First(&r).Error; err != nil {
		if err == gorm.ErrRecordNotFound {
			return nil, nil
		}
		return nil, err
	}
	var err error
	var tasks []*model.WorkflowTask
	var childTasks []*model.WorkflowTaskRelation
	var taskActions []*model.WorkflowTaskAction
	var handlers []func() error
	handlers = append(handlers, func() error {
		tasks, err = w.selectTask(context.Background(), r.WorkflowID, []string{r.TaskID})
		return err
	})
	handlers = append(handlers, func() error {
		childTasks, err = w.selectTaskRelation(context.Background(), r.WorkflowID, r.TaskID)
		return err
	})
	handlers = append(handlers, func() error {
		taskActions, err = w.selectTaskAction(context.Background(), r.WorkflowID, []string{r.TaskID})
		if err != nil {
			return err
		}
		return nil
	})
	if err = util.GoAndWait(handlers...); err != nil {
		return nil, err
	}
	return w.completeTaskInstance(r, tasks, childTasks, taskActions)
}

// Delete delete workflow and relate info
func (w *workflowDALImpl) Delete(ctx context.Context, workflowID string) error {
	return w.delete(workflowDB, workflowID)
}

func (w *workflowDALImpl) Save(ctx context.Context, record *model.Workflow) error {
	return workflowDB.WithContext(ctx).Transaction(func(tx *gorm.DB) error {
		// first delete and insert
		if len(record.WorkflowID) > 0 {
			if err := w.delete(tx, record.WorkflowID); err != nil {
				return err
			}
		}
		return w.create(ctx, tx, record)
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

func (w *workflowDALImpl) UpdateInstance(ctx context.Context, record *model.WorkflowInstance) error {
	var condition = model.WorkflowInstance{WorkflowInstanceID: record.WorkflowInstanceID}
	record.UpdateTime = time.Now()
	return workflowDB.WithContext(ctx).Where(&condition).Updates(&record).Error
}

func (w *workflowDALImpl) UpdateTaskInstance(tx *gorm.DB, record *model.WorkflowTaskInstance) error {
	var condition = model.WorkflowTaskInstance{ID: record.ID}
	record.UpdateTime = time.Now()
	return tx.Where(&condition).Updates(&record).Error
}

func (w *workflowDALImpl) delete(tx *gorm.DB, workflowID string) error {
	var handlers []func() error
	handlers = append(handlers, func() error {
		record := model.Workflow{Status: constants.InvalidStatus, UpdateTime: time.Now()}
		return tx.Where("workflow_id = ?", workflowID).Updates(&record).Error
	}, func() error {
		record := model.WorkflowTask{Status: constants.InvalidStatus, UpdateTime: time.Now()}
		return tx.Where("workflow_id = ?", workflowID).Updates(&record).Error
	}, func() error {
		record := model.WorkflowTaskRelation{Status: constants.InvalidStatus,
			UpdateTime: time.Now()}
		return tx.Where("workflow_id = ?", workflowID).Updates(&record).Error
	}, func() error {
		record := model.WorkflowTaskAction{Status: constants.InvalidStatus,
			UpdateTime: time.Now()}
		return tx.Where("workflow_id = ?", workflowID).Updates(&record).Error
	}, func() error {
		record := model.WorkflowInstance{WorkflowStatus: constants.InvalidStatus,
			UpdateTime: time.Now()}
		return tx.Where("workflow_id = ?", workflowID).Updates(&record).Error
	}, func() error {
		record := model.WorkflowTaskInstance{Status: constants.InvalidStatus,
			UpdateTime: time.Now()}
		return tx.Where("workflow_id = ?", workflowID).Updates(&record).Error
	})
	return util.GoAndWait(handlers...)
}

func (w *workflowDALImpl) create(ctx context.Context, tx *gorm.DB, record *model.Workflow) error {
	wf, err := swf.Parse(record.Definition)
	if err != nil {
		return err
	}
	if wf == nil {
		return errors.New("workflow text invalid")
	}
	r, err := w.Select(ctx, tx, wf.ID)
	if err != nil {
		return err
	}
	if r != nil {
		return errors.New("workflow id already exists")
	}
	var insertData = model.Workflow{}
	insertData.WorkflowID = wf.ID
	insertData.WorkflowName = wf.Name
	insertData.Version = wf.Version
	insertData.Definition = record.Definition
	insertData.Status = constants.NormalStatus
	insertData.CreateTime = time.Now()
	insertData.UpdateTime = time.Now()
	var handlers []func() error
	handlers = append(handlers, func() error {
		return tx.Create(insertData).Error
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
		task.Status = constants.NormalStatus
		task.TaskType = gconv.String(state.GetType())
		task.CreateTime = time.Now()
		task.UpdateTime = time.Now()
		task.Actions = w.buildTaskAction(task.TaskID, workflow, state)
		w.fillTaskFilterIfExist(state, &task)
		tasks = append(tasks, &task)
	}
	return tasks
}

func (w *workflowDALImpl) fillTaskFilterIfExist(workflowState pmodel.State, task *model.WorkflowTask) {
	filter := workflowState.GetStateDataFilter()
	if filter != nil {
		task.TaskInputFilter = filter.Input
	}
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
			fallthrough
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
		taskAction.Status = constants.NormalStatus
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
			taskAction.Status = constants.NormalStatus
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
	r.Status = constants.NormalStatus
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
		r.Status = constants.NormalStatus
		r.CreateTime = time.Now()
		r.UpdateTime = time.Now()
		rel = append(rel, &r)
	}
	for _, condition := range s.DataConditions {
		var r = model.WorkflowTaskRelation{}
		r.WorkflowID = workflow.ID
		r.FromTaskID = taskIDs[state.GetName()]
		r.Status = constants.NormalStatus
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
	r.Status = constants.NormalStatus
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

func (w *workflowDALImpl) selectTaskRelation(ctx context.Context, workflowID string, taskID string) (
	[]*model.WorkflowTaskRelation, error) {
	var relations []*model.WorkflowTaskRelation
	var c = model.WorkflowTaskRelation{FromTaskID: taskID, WorkflowID: workflowID, Status: constants.NormalStatus}
	if err := workflowDB.WithContext(ctx).Where(&c).Find(&relations).Error; err != nil {
		return nil, err
	}
	return relations, nil
}

func (w *workflowDALImpl) completeTaskInstance(instance model.WorkflowTaskInstance, tasks []*model.WorkflowTask,
	childTasks []*model.WorkflowTaskRelation, taskActions []*model.WorkflowTaskAction) (*model.WorkflowTaskInstance, error) {
	if len(tasks) == 0 {
		return nil, nil
	}
	var r model.WorkflowTaskInstance
	if err := gconv.Struct(instance, &r); err != nil {
		return nil, err
	}
	r.Task = tasks[0]
	r.Task.ChildTasks = childTasks
	r.Task.Actions = taskActions
	return &r, nil
}

func (w *workflowDALImpl) fillInstanceCount(workflows []model.Workflow) error {
	var handlers []func() error
	for idx := range workflows {
		idx := idx
		handlers = append(handlers, func() error {
			var instances []model.WorkflowInstance
			if err := workflowDB.Where("workflow_id = ?", workflows[idx].WorkflowID).
				Where("workflow_status != ?", constants.InvalidStatus).Find(&instances).Error; err != nil {
				return err
			}
			if len(instances) == 0 {
				return nil
			}
			workflows[idx].TotalInstances = len(instances)
			for _, instance := range instances {
				if instance.WorkflowStatus == constants.TaskInstanceFailStatus {
					workflows[idx].TotalFailedInstances++
				} else {
					workflows[idx].TotalRunningInstances++
				}
			}
			return nil
		})
	}
	return util.GoAndWait(handlers...)
}
