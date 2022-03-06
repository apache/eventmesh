package tcp

import gtcp "eventmesh/common/protocol/tcp"

type EventMeshTCPClient interface {
	Init()
	Publish(msg interface{}, timeout int64) gtcp.Package
	GetPubClient() EventMeshTCPPubClient
	GetSubClient() EventMeshTCPSubClient
}
