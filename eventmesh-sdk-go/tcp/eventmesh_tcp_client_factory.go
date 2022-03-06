package tcp

import (
	"eventmesh/common/protocol"
	"eventmesh/tcp/conf"
)

func CreateEventMeshTCPClient(eventMeshTcpClientConfig conf.EventMeshTCPClientConfig, messageType protocol.MessageType) EventMeshTCPClient {

	if messageType == protocol.DefaultMessageType.CloudEvent {
		return NewCloudEventTCPClient(eventMeshTcpClientConfig)
	}

	return nil
}
