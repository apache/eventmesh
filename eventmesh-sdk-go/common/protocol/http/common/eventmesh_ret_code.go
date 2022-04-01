package common

type EventMeshRetCode struct {
	RetCode int    `json:"retCode"`
	ErrMsg  string `json:"errMsg"`
}

var DefaultEventMeshRetCode = struct {
	SUCCESS EventMeshRetCode
}{
	SUCCESS: EventMeshRetCode{RetCode: 0, ErrMsg: "success"},
}
