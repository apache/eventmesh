package client

import (
	"eventmesh/common/protocol/http/body"
)

var HeartbeatRequestBodyKey = struct {
	CLIENTTYPE        string
	CONSUMERGROUP     string
	HEARTBEATENTITIES string
}{
	CLIENTTYPE:        "clientType",
	HEARTBEATENTITIES: "heartbeatEntities",
	CONSUMERGROUP:     "consumerGroup",
}

type HeartbeatEntity struct {
	Topic      string `json:"topic"`
	Url        string `json:"url"`
	ServiceId  string `json:"serviceId"`
	InstanceId string `json:"instanceId"`
}

type HeartbeatRequestBody struct {
	body.Body
	consumerGroup     string
	clientType        string
	heartbeatEntities string
}

func (h *HeartbeatRequestBody) ConsumerGroup() string {
	return h.consumerGroup
}

func (h *HeartbeatRequestBody) SetConsumerGroup(consumerGroup string) {
	h.consumerGroup = consumerGroup
}

func (h *HeartbeatRequestBody) ClientType() string {
	return h.clientType
}

func (h *HeartbeatRequestBody) SetClientType(clientType string) {
	h.clientType = clientType
}

func (h *HeartbeatRequestBody) HeartbeatEntities() string {
	return h.heartbeatEntities
}

func (h *HeartbeatRequestBody) SetHeartbeatEntities(heartbeatEntities string) {
	h.heartbeatEntities = heartbeatEntities
}

func (h *HeartbeatRequestBody) BuildBody(bodyParam map[string]interface{}) *HeartbeatRequestBody {
	return nil
}
