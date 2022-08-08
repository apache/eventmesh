package task

import (
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/internal/constants"
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/internal/dal/model"
)

type EventTask struct {
	BaseTask
	Actions    []*model.WorkflowTaskAction
	Transition *model.WorkflowTaskRelation
}

func (t *EventTask) Type() string {
	return constants.TaskTypeEvent
}

func (t *EventTask) Run() error {
	return nil
}
