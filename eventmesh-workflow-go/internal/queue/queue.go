package queue

import "github.com/apache/incubator-eventmesh/eventmesh-workflow-go/internal/dal/model"

type ObserveQueue interface {
	Name() string

	Publish(tasks []*model.WorkflowTaskInstance) error

	Ack(tasks *model.WorkflowTaskInstance) error

	Observe()
}

var queueFactory = make(map[string]ObserveQueue)

// RegisterQueue registers a queue by its name.
func RegisterQueue(q ObserveQueue) {
	queueFactory[q.Name()] = q
}

// GetQueue returns the queue by name.
func GetQueue(name string) ObserveQueue {
	return queueFactory[name]
}
