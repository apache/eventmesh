package producer

type EventMeshProtocolProducer interface {
	Publish(eventMeshMessage interface{})
}
