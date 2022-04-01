package tcp

import (
	gtcp "eventmesh/common/protocol/tcp"
	"eventmesh/tcp/conf"
)

type CloudEventTCPClient struct {
	cloudEventTCPPubClient *CloudEventTCPPubClient
	cloudEventTCPSubClient *CloudEventTCPSubClient
}

func NewCloudEventTCPClient(eventMeshTcpClientConfig conf.EventMeshTCPClientConfig) *CloudEventTCPClient {
	return &CloudEventTCPClient{
		cloudEventTCPPubClient: NewCloudEventTCPPubClient(eventMeshTcpClientConfig),
		cloudEventTCPSubClient: NewCloudEventTCPSubClient(eventMeshTcpClientConfig),
	}
}

func (c *CloudEventTCPClient) Init() {
	c.cloudEventTCPPubClient.init()
	c.cloudEventTCPSubClient.init()
}

func (c *CloudEventTCPClient) Publish(message interface{}, timeout int64) gtcp.Package {
	return c.cloudEventTCPPubClient.publish(message, timeout)
}

func (c *CloudEventTCPClient) GetPubClient() EventMeshTCPPubClient {
	return c.cloudEventTCPPubClient
}

func (c *CloudEventTCPClient) GetSubClient() EventMeshTCPSubClient {
	return c.cloudEventTCPSubClient
}
