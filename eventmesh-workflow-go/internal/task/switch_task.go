package task

import (
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/internal/constants"
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/internal/dal/model"
)

type SwitchTask struct {
	BaseTask
	Transitions []*model.WorkflowTaskRelation
}

func (t *SwitchTask) Type() string {
	return constants.TaskTypeSwitch
}

func (t *SwitchTask) Run() error {
	return nil
}
