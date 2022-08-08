package schedule

import (
	"context"
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/config"
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/internal/constants"
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/internal/dal"
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/internal/dal/model"
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/internal/task"
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/log"
	"time"
)

const (
	schedulerLockKey     = "scheduler_lock"
	schedulerLockTimeout = 2
)

type Scheduler struct {
	workflowDAL dal.WorkflowDAL
}

func NewScheduler() (*Scheduler, error) {
	var s Scheduler
	s.workflowDAL = dal.NewWorkflowDAL()
	return &s, nil
}

func (s *Scheduler) Run() {
	go func() {
		ticker := time.NewTicker(time.Millisecond * time.Duration(config.GlobalConfig().Flow.Schedule.Interval))
		defer func() {
			if err := recover(); err != nil {
				log.Get(constants.LogSchedule).Errorf("schedule run error=%+v", err)
			}
			ticker.Stop()
		}()

		for range ticker.C {
			s.handle()
		}
	}()
}

func (s *Scheduler) handle() {
	var res *model.WorkflowTaskInstance
	if err := lock(func() error {
		var err error
		res, err = s.workflowDAL.SelectTaskInstance(context.Background(),
			model.WorkflowTaskInstance{Status: constants.TaskInstanceWaitStatus})
		if err != nil {
			return err
		}
		if res == nil {
			return nil
		}
		if err = s.workflowDAL.UpdateTaskInstance(context.Background(), &model.WorkflowTaskInstance{ID: res.ID,
			Status: constants.TaskInstanceProcessStatus}); err != nil {
			return err
		}
		return nil
	}); err != nil {
		log.Get(constants.LogSchedule).Errorf("handle UpdateTaskInstance error=%v", err)
		return
	}
	if res == nil {
		return
	}
	t := task.Build(res)
	if t == nil {
		return
	}
	// TODO fail retry
	if err := t.Run(); err != nil {
		if err := s.workflowDAL.UpdateTaskInstance(context.Background(), &model.WorkflowTaskInstance{ID: res.ID,
			Status: constants.TaskInstanceFailStatus}); err != nil {
			log.Get(constants.LogSchedule).Errorf("handle UpdateTaskInstance error=%v", err)
			return
		}
	}
	// success
	if err := s.workflowDAL.UpdateTaskInstance(context.Background(), &model.WorkflowTaskInstance{ID: res.ID,
		Status: constants.TaskInstanceSuccessStatus}); err != nil {
		// TODO fail retry
		log.Get(constants.LogSchedule).Errorf("handle UpdateTaskInstance error=%v", err)
		return
	}
}

func lock(h func() error) error {
	l, err := dal.GetLockClient().ObtainTimeout(schedulerLockKey, schedulerLockTimeout)
	if err != nil {
		return err
	}
	defer l.Release()
	return h()
}
