package tcp

import (
	"eventmesh/common/protocol/tcp"
	"eventmesh/tcp/conf"
	"eventmesh/tcp/utils"
)

type CloudEventTCPPubClient struct {
	*BaseTCPClient
}

func NewCloudEventTCPPubClient(eventMeshTcpClientConfig conf.EventMeshTCPClientConfig) *CloudEventTCPPubClient {
	return &CloudEventTCPPubClient{BaseTCPClient: NewBaseTCPClient(eventMeshTcpClientConfig)}
}

func (c CloudEventTCPPubClient) init() {
	c.Open()
	c.Hello()
	c.Heartbeat()
}

func (c CloudEventTCPPubClient) reconnect() {
	c.Reconnect()
	c.Heartbeat()
}

func (c CloudEventTCPPubClient) publish(message interface{}, timeout int64) tcp.Package {
	msg := utils.BuildPackage(message, tcp.DefaultCommand.ASYNC_MESSAGE_TO_SERVER)
	return c.IO(msg, timeout)
}
