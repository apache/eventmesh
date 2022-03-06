package common

type ClientInstanceKey struct {
	//Protocol layer requester description
	ENV      string
	IDC      string
	SYS      string
	PID      string
	IP       string
	USERNAME string
	PASSWORD string
	BIZSEQNO string
	UNIQUEID string
}

type EventMeshInstanceKey struct {
	//Protocol layer EventMesh description
	EVENTMESHCLUSTER string
	EVENTMESHIP      string
	EVENTMESHENV     string
	EVENTMESHIDC     string
}

var ProtocolKey = struct {
	REQUEST_CODE     string
	LANGUAGE         string
	VERSION          string
	PROTOCOL_TYPE    string
	PROTOCOL_VERSION string
	PROTOCOL_DESC    string

	ClientInstanceKey ClientInstanceKey

	EventMeshInstanceKey EventMeshInstanceKey

	//return of CLIENT <-> EventMesh
	RETCODE string
	RETMSG  string
	RESTIME string
}{
	REQUEST_CODE:     "code",
	LANGUAGE:         "language",
	VERSION:          "version",
	PROTOCOL_TYPE:    "protocoltype",
	PROTOCOL_VERSION: "protocolversion",
	PROTOCOL_DESC:    "protocoldesc",

	ClientInstanceKey: ClientInstanceKey{
		ENV:      "env",
		IDC:      "idc",
		SYS:      "sys",
		PID:      "pid",
		IP:       "ip",
		USERNAME: "username",
		PASSWORD: "passwd",
		BIZSEQNO: "bizseqno",
		UNIQUEID: "uniqueid",
	},

	EventMeshInstanceKey: EventMeshInstanceKey{
		EVENTMESHCLUSTER: "eventmeshcluster",
		EVENTMESHIP:      "eventmeship",
		EVENTMESHENV:     "eventmeshenv",
		EVENTMESHIDC:     "eventmeshidc",
	},

	RETCODE: "retCode",
	RETMSG:  "retMsg",
	RESTIME: "resTime",
}
