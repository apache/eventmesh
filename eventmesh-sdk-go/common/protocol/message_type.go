package protocol

type MessageType string

var DefaultMessageType = struct {
	CloudEvent       MessageType
	OpenMessage      MessageType
	EventMeshMessage MessageType
}{
	CloudEvent:       "CloudEvent",
	OpenMessage:      "OpenMessage",
	EventMeshMessage: "EventMeshMessage",
}
