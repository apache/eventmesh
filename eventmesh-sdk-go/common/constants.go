package common

var Constants = struct {
	LANGUAGE_GO                 string
	HTTP_PROTOCOL_PREFIX        string
	HTTPS_PROTOCOL_PREFIX       string
	PROTOCOL_TYPE               string
	PROTOCOL_VERSION            string
	PROTOCOL_DESC               string
	DEFAULT_HTTP_TIME_OUT       int64
	EVENTMESH_MESSAGE_CONST_TTL string

	// Client heartbeat interval
	HEARTBEAT int64

	// Protocol type
	CLOUD_EVENTS_PROTOCOL_NAME string
	EM_MESSAGE_PROTOCOL_NAME   string
	OPEN_MESSAGE_PROTOCOL_NAME string
}{
	LANGUAGE_GO:                 "GO",
	HTTP_PROTOCOL_PREFIX:        "http://",
	HTTPS_PROTOCOL_PREFIX:       "https://",
	PROTOCOL_TYPE:               "protocoltype",
	PROTOCOL_VERSION:            "protocolversion",
	PROTOCOL_DESC:               "protocoldesc",
	DEFAULT_HTTP_TIME_OUT:       15000,
	EVENTMESH_MESSAGE_CONST_TTL: "ttl",
	HEARTBEAT:                   30 * 1000,
	CLOUD_EVENTS_PROTOCOL_NAME:  "cloudevents",
	EM_MESSAGE_PROTOCOL_NAME:    "eventmeshmessage",
	OPEN_MESSAGE_PROTOCOL_NAME:  "openmessage",
}
