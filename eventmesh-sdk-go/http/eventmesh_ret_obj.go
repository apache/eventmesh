package http

type EventMeshRetObj struct {
	ResTime int64  `json:"resTime"`
	RetCode int    `json:"retCode"`
	RetMsg  string `json:"retMsg"`
}
