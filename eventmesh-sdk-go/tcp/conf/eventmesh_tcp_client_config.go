package conf

import "eventmesh/common/protocol/tcp"

type EventMeshTCPClientConfig struct {
	host      string
	port      int
	userAgent tcp.UserAgent
}

func NewEventMeshTCPClientConfig(host string, port int, userAgent tcp.UserAgent) *EventMeshTCPClientConfig {
	return &EventMeshTCPClientConfig{host: host, port: port, userAgent: userAgent}
}

func (e *EventMeshTCPClientConfig) Host() string {
	return e.host
}

func (e *EventMeshTCPClientConfig) SetHost(host string) {
	e.host = host
}

func (e *EventMeshTCPClientConfig) Port() int {
	return e.port
}

func (e *EventMeshTCPClientConfig) SetPort(port int) {
	e.port = port
}

func (e *EventMeshTCPClientConfig) UserAgent() tcp.UserAgent {
	return e.userAgent
}

func (e *EventMeshTCPClientConfig) SetUserAgent(userAgent tcp.UserAgent) {
	e.userAgent = userAgent
}
