package protocol

type SubscriptionType string

var DefaultSubscriptionType = struct {
	SYNC  SubscriptionType
	ASYNC SubscriptionType
}{
	SYNC:  "SYNC",
	ASYNC: "ASYNC",
}
