package protocol

type SubscriptionMode string

var DefaultSubscriptionMode = struct {
	BROADCASTING SubscriptionMode
	CLUSTERING   SubscriptionMode
}{
	BROADCASTING: "BROADCASTING",
	CLUSTERING:   "CLUSTERING",
}
