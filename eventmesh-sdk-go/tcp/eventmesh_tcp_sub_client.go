package tcp

import "eventmesh/common/protocol"

type EventMeshTCPSubClient interface {
	init()
	subscribe(topic string, subscriptionMode protocol.SubscriptionMode, subscriptionType protocol.SubscriptionType)
	unsubscribe()
}
