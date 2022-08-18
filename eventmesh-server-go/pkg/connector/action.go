package standalone

type EventMeshAction string

const (
	CommitMessage  EventMeshAction = "CommitMessage"
	ReconsumeLater EventMeshAction = "ReconsumeLater"
	ManualAck      EventMeshAction = "ManualAck"
)
