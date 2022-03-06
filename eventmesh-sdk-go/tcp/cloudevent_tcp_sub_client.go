package tcp

import (
	"eventmesh/common/protocol"
	"eventmesh/tcp/conf"
)

type CloudEventTCPSubClient struct {
	*BaseTCPClient
}

func NewCloudEventTCPSubClient(eventMeshTcpClientConfig conf.EventMeshTCPClientConfig) *CloudEventTCPSubClient {
	return &CloudEventTCPSubClient{BaseTCPClient: NewBaseTCPClient(eventMeshTcpClientConfig)}
}

func (c CloudEventTCPSubClient) init() {
	//panic("implement me")
}

func (c CloudEventTCPSubClient) subscribe(topic string, subscriptionMode protocol.SubscriptionMode, subscriptionType protocol.SubscriptionType) {
	panic("implement me")
}

func (c CloudEventTCPSubClient) unsubscribe() {
	panic("implement me")
}
