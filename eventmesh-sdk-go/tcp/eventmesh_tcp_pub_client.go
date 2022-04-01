package tcp

import gtcp "eventmesh/common/protocol/tcp"

type EventMeshTCPPubClient interface {
	init()
	reconnect()
	publish(message interface{}, timeout int64) gtcp.Package
}
