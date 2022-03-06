package client

import (
	"eventmesh/common/protocol"
	"eventmesh/common/protocol/http/body"
)

var SubscribeRequestBodyKey = struct {
	TOPIC         string
	URL           string
	CONSUMERGROUP string
}{
	TOPIC:         "topic",
	URL:           "url",
	CONSUMERGROUP: "consumerGroup",
}

type SubscribeRequestBody struct {
	body.Body
	topics        []protocol.SubscriptionItem
	url           string
	consumerGroup string
}
