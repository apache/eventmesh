package task

import (
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/internal/dal/model"
)

type Task interface {
	Type() string

	Run() error
}

type BaseTask struct {
	TaskID         string
	TaskInstID     string
	WorkflowID     string
	WorkflowInstID string
}

func Build(instance *model.WorkflowTaskInstance) Task {
	return &OperationTask{}
}
