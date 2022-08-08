package task

import (
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/internal/constants"
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/internal/dal/model"
)

type OperationTask struct {
	BaseTask
	Actions    []*model.WorkflowTaskAction
	Transition *model.WorkflowTaskRelation
}

func (t *OperationTask) Type() string {
	return constants.TaskTypeOperation
}

func (t *OperationTask) Run() error {
	return nil
}
