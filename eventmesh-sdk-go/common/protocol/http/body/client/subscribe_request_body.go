package client

import (
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/common/protocol"
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/common/protocol/http/body"
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
