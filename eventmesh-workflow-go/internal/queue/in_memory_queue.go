package queue

import (
	"context"
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/internal/constants"
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/internal/dal"
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/internal/dal/model"
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/log"
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
	if err := q.workflowDAL.InsertTaskInstance(context.Background(), v); err != nil {
		log.Get(constants.LogQueue).Errorf("Observe InsertTaskInstance error=%v", err)
	}
}
