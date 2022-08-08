package flow

import (
	"context"
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/config"
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
