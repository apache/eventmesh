package common

type RequestCode struct {
	RequestCode int    `json:"requestCode"`
	Desc        string `json:"desc"`
}

var DefaultRequestCode = struct {
	MSG_BATCH_SEND         RequestCode
	MSG_BATCH_SEND_V2      RequestCode
	MSG_SEND_SYNC          RequestCode
	MSG_SEND_ASYNC         RequestCode
	HTTP_PUSH_CLIENT_ASYNC RequestCode
	HTTP_PUSH_CLIENT_SYNC  RequestCode
	REGISTER               RequestCode
	UNREGISTER             RequestCode
	HEARTBEAT              RequestCode
	SUBSCRIBE              RequestCode
	UNSUBSCRIBE            RequestCode
	REPLY_MESSAGE          RequestCode
	ADMIN_METRICS          RequestCode
	ADMIN_SHUTDOWN         RequestCode
}{
	MSG_BATCH_SEND: RequestCode{
		RequestCode: 102,
		Desc:        "SEND BATCH MSG",
	},
	MSG_BATCH_SEND_V2: RequestCode{
		RequestCode: 107,
		Desc:        "SEND BATCH MSG V2",
	},
	MSG_SEND_SYNC: RequestCode{
		RequestCode: 101,
		Desc:        "SEND SINGLE MSG SYNC",
	},
	MSG_SEND_ASYNC: RequestCode{
		RequestCode: 104,
		Desc:        "SEND SINGLE MSG ASYNC",
	},
	HTTP_PUSH_CLIENT_ASYNC: RequestCode{
		RequestCode: 105,
		Desc:        "PUSH CLIENT BY HTTP POST",
	},
	HTTP_PUSH_CLIENT_SYNC: RequestCode{
		RequestCode: 106,
		Desc:        "PUSH CLIENT BY HTTP POST",
	},
	REGISTER: RequestCode{
		RequestCode: 201,
		Desc:        "REGISTER",
	},
	UNREGISTER: RequestCode{
		RequestCode: 202,
		Desc:        "UNREGISTER",
	},
	HEARTBEAT: RequestCode{
		RequestCode: 203,
		Desc:        "HEARTBEAT",
	},
	SUBSCRIBE: RequestCode{
		RequestCode: 206,
		Desc:        "SUBSCRIBE",
	},
	UNSUBSCRIBE: RequestCode{
		RequestCode: 207,
		Desc:        "UNSUBSCRIBE",
	},
	REPLY_MESSAGE: RequestCode{
		RequestCode: 301,
		Desc:        "REPLY MESSAGE",
	},
	ADMIN_METRICS: RequestCode{
		RequestCode: 603,
		Desc:        "ADMIN METRICS",
	},
	ADMIN_SHUTDOWN: RequestCode{
		RequestCode: 601,
		Desc:        "ADMIN SHUTDOWN",
	},
}
