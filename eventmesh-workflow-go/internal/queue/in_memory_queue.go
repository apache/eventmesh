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

package queue

import (
	"context"
	"fmt"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/log"
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/internal/constants"
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/internal/dal"
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/internal/dal/model"
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/internal/metrics"
	"github.com/gogf/gf/util/gconv"
	"github.com/reactivex/rxgo/v2"
)

func init() {
	RegisterQueue(newInMemoryQueue())
}

type inMemoryQueue struct {
	ch          chan rxgo.Item
	observable  rxgo.Observable
	workflowDAL dal.WorkflowDAL
}

func newInMemoryQueue() ObserveQueue {
	var p inMemoryQueue
	p.ch = make(chan rxgo.Item)
	p.observable = rxgo.FromChannel(p.ch)
	p.workflowDAL = dal.NewWorkflowDAL()
	return &p
}

// Name returns queue's name.
func (q *inMemoryQueue) Name() string {
	return constants.QueueTypeInMemory
}

func (q *inMemoryQueue) Publish(tasks []*model.WorkflowTaskInstance) error {
	if len(tasks) == 0 {
		return nil
	}
	metrics.Add(constants.MetricsTaskQueue, fmt.Sprintf("%s_%s", q.Name(), constants.MetricsQueueSize),
		float64(len(tasks)))
	for _, t := range tasks {
		q.ch <- rxgo.Of(t)
	}
	return nil
}

func (q *inMemoryQueue) Ack(tasks *model.WorkflowTaskInstance) error {
	return nil
}

func (q *inMemoryQueue) Observe() {
	go func() {
		defer func() {
			if err := recover(); err != nil {
				log.Get(constants.LogQueue).Errorf("Observe error=%+v", err)
			}
		}()
		for item := range q.observable.Observe() {
			metrics.Dec(constants.MetricsTaskQueue, fmt.Sprintf("%s_%s", q.Name(), constants.MetricsQueueSize))
			q.handle(item)
		}
	}()
}
func (q *inMemoryQueue) handle(item rxgo.Item) {
	v, ok := item.V.(*model.WorkflowTaskInstance)
	if !ok {
		return
	}
	log.Get(constants.LogQueue).Infof("handle=%s", gconv.String(v))
	if v.ID != 0 {
		if err := q.workflowDAL.UpdateTaskInstance(dal.GetDalClient(), v); err != nil {
			log.Get(constants.LogQueue).Errorf("Observe UpdateTaskInstance error=%v", err)
		}
		return
	}
	if err := q.workflowDAL.InsertTaskInstance(context.Background(), v); err != nil {
		log.Get(constants.LogQueue).Errorf("Observe InsertTaskInstance error=%v", err)
	}
}
