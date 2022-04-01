package common

type ClientType struct {
	Type int    `json:"type"`
	Desc string `json:"desc"`
}

var DefaultClientType = struct {
	PUB ClientType
	SUB ClientType
}{
	PUB: ClientType{
		Type: 1,
		Desc: "Client for publishing",
	},
	SUB: ClientType{
		Type: 2,
		Desc: "Client for subscribing",
	},
}
