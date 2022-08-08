package flow

type WorkflowParam struct {
	ID         string `json:"id"`
	InstanceID string `json:"instance_id"`
	Input      string `json:"input"`
}
