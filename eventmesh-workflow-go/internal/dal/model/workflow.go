package model

import "time"

type Workflow struct {
	ID           int       `json:"id" gorm:"column:id;type:int;primaryKey;autoIncrement"`
	WorkflowID   string    `json:"workflow_id" gorm:"column:workflow_id;type:varchar;size:1024"`
	WorkflowName string    `json:"workflow_name" gorm:"column:workflow_name;type:varchar;size:1024"`
	Definition   string    `json:"definition" gorm:"column:definition;type:text;"`
	Status       int       `json:"status" gorm:"column:status;type:int"`
	Version      string    `json:"version" gorm:"column:version;type:varchar;size:64"`
	CreateTime   time.Time `json:"create_time"`
	UpdateTime   time.Time `json:"update_time"`
}

func (w Workflow) TableName() string {
	return "t_workflow"
}

type WorkflowInstance struct {
	ID                 int       `json:"id" gorm:"column:id;type:int;primaryKey;autoIncrement"`
	WorkflowID         string    `json:"workflow_id" gorm:"column:workflow_id;type:varchar;size:1024"`
	WorkflowInstanceID string    `json:"workflow_instance_id" gorm:"column:workflow_instance_id;type:varchar;size:1024"`
	WorkflowStatus     int       `json:"workflow_status" gorm:"column:workflow_status;type:int"`
	CreateTime         time.Time `json:"create_time"`
	UpdateTime         time.Time `json:"update_time"`
}

func (w WorkflowInstance) TableName() string {
	return "t_workflow_instance"
}
