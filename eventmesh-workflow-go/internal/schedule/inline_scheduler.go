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

package schedule

import (
	"context"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/log"
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/config"
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/internal/constants"
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/internal/dal"
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/internal/dal/model"
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/internal/filter"
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/internal/metrics"
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/internal/task"
	"github.com/avast/retry-go/v4"
	"gorm.io/gorm"
	"time"
)

func init() {
	schedulerBuilder["in-line"] = NewInlineScheduler
}

const (
	schedulerLockKey     = "scheduler_lock"
	schedulerLockTimeout = 2
)

type inlineScheduler struct {
	workflowDAL dal.WorkflowDAL
}

func NewInlineScheduler() Scheduler {
	var s inlineScheduler
	s.workflowDAL = dal.NewWorkflowDAL()
	return &s
}

func (s *inlineScheduler) Run() {
	go func() {
		ticker := time.NewTicker(time.Millisecond * time.Duration(config.Get().Flow.Scheduler.Interval))
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

func (s *inlineScheduler) handle() {
	if err := dal.GetDalClient().Transaction(func(tx *gorm.DB) error {
		var res *model.WorkflowTaskInstance
		if err := s.lock(func() error {
			var err error
			res, err = s.fetchTask(tx)
			if err != nil {
				return err
			}
			return nil
		}); err != nil {
			return err
		}
		if res == nil {
			return nil
		}

		// filter input data
		filter.FilterWorkflowTaskInputData(res)

		t := task.New(res)
		if t == nil {
			return nil
		}
		if err := retry.Do(func() error {
			return t.Run()
		}, retry.Attempts(constants.RetryAttempts)); err != nil {
			return s.workflowDAL.UpdateTaskInstance(tx, &model.WorkflowTaskInstance{ID: res.ID,
				Status: constants.TaskInstanceFailStatus})
		}
		return s.workflowDAL.UpdateTaskInstance(tx, &model.WorkflowTaskInstance{ID: res.ID,
			Status: constants.TaskInstanceSuccessStatus})
	}); err != nil {
		log.Get(constants.LogSchedule).Errorf("handle UpdateTaskInstance error=%v", err)
	}
}

func (s *inlineScheduler) lock(h func() error) error {
	start := time.Now()
	l, err := dal.GetLockClient().ObtainTimeout(schedulerLockKey, schedulerLockTimeout)
	elapsed := time.Since(start).Milliseconds()
	metrics.RecordLatency(constants.MetricsScheduler, constants.MetricsDbLockAcquireTime, float64(elapsed))
	if err != nil {
		return err
	}
	defer l.Release()
	return h()
}

func (s *inlineScheduler) fetchTask(tx *gorm.DB) (*model.WorkflowTaskInstance, error) {
	var err error
	res, err := s.workflowDAL.SelectTaskInstance(context.Background(),
		model.WorkflowTaskInstance{Status: constants.TaskInstanceWaitStatus})
	if err != nil {
		return nil, err
	}
	if res == nil {
		return nil, nil
	}
	if err = s.workflowDAL.UpdateTaskInstance(tx, &model.WorkflowTaskInstance{ID: res.ID,
		Status: constants.TaskInstanceProcessStatus}); err != nil {
		return nil, err
	}
	return res, nil
}
