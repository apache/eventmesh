package protocol

type EventMeshMessage struct {
	BizSeqNo string            `json:"biz_seq_no"`
	UniqueId string            `json:"unique_id"`
	Topic    string            `json:"topic"`
	Content  string            `json:"content"`
	Prop     map[string]string `json:"prop"`
}
