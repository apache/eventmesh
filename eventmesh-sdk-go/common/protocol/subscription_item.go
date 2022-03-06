package protocol

type SubscriptionItem struct {
	Topic string           `json:"topic"`
	Mode  SubscriptionMode `json:"mode"`
	Type  SubscriptionType `json:"type"`
}
