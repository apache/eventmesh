package common

var EventMeshCommon = struct {
	// Timeout time shared by the server
	DEFAULT_TIME_OUT_MILLS int

	// User agent
	USER_AGENT_PURPOSE_PUB string
	USER_AGENT_PURPOSE_SUB string

	// Protocol type
	CLOUD_EVENTS_PROTOCOL_NAME string
	EM_MESSAGE_PROTOCOL_NAME   string
	OPEN_MESSAGE_PROTOCOL_NAME string
}{
	DEFAULT_TIME_OUT_MILLS:     20 * 1000,
	USER_AGENT_PURPOSE_PUB:     "pub",
	USER_AGENT_PURPOSE_SUB:     "sub",
	CLOUD_EVENTS_PROTOCOL_NAME: "cloudevents",
	EM_MESSAGE_PROTOCOL_NAME:   "eventmeshmessage",
	OPEN_MESSAGE_PROTOCOL_NAME: "openmessage",
}
