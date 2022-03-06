package producer

import (
	"eventmesh/http/conf"
	cloudevents "github.com/cloudevents/sdk-go/v2"
)

type EventMeshHttpProducer struct {
	cloudEventProducer *CloudEventProducer
}

func NewEventMeshHttpProducer(eventMeshHttpClientConfig conf.EventMeshHttpClientConfig) *EventMeshHttpProducer {
	return &EventMeshHttpProducer{
		cloudEventProducer: NewCloudEventProducer(eventMeshHttpClientConfig),
	}
}

func (e *EventMeshHttpProducer) Publish(eventMeshMessage interface{}) {

	// FIXME Check eventMeshMessage is not nil

	// CloudEvent
	if _, ok := eventMeshMessage.(cloudevents.Event); ok {
		event := eventMeshMessage.(cloudevents.Event)
		e.cloudEventProducer.Publish(event)
	}
}
