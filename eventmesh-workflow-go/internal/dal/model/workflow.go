// Licensed to the Apache Software Foundation (ASF) under one or more
// contributor license agreements.  See the NOTICE file distributed with
// this work for additional information regarding copyright ownership.
// The ASF licenses this file to You under the Apache License, Version 2.0
// (the "License"); you may not use this file except in compliance with
// the License.  You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package model

import "time"

// Workflow workflow model definition
type Workflow struct {
	ID                    int       `json:"id" gorm:"column:id;type:int;primaryKey;autoIncrement"`
	WorkflowID            string    `json:"workflow_id" gorm:"column:workflow_id;type:varchar;size:1024"`
	WorkflowName          string    `json:"workflow_name" gorm:"column:workflow_name;type:varchar;size:1024"`
	Definition            string    `json:"definition" gorm:"column:definition;type:text;"`
	Status                int       `json:"status" gorm:"column:status;type:int"`
	Version               string    `json:"version" gorm:"column:version;type:varchar;size:64"`
	CreateTime            time.Time `json:"create_time"`
	UpdateTime            time.Time `json:"update_time"`
	TotalInstances        int       `json:"total_instances" gorm:"-"`
	TotalRunningInstances int       `json:"total_running_instances" gorm:"-"`
	TotalFailedInstances  int       `json:"total_failed_instances" gorm:"-"`
}

// TableName workflow table name define
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

type QueryParam struct {
	WorkflowID string `json:"workflow_id"`
	Status     int    `json:"status"`
	Page       int    `json:"page"` // page num
	Size       int    `json:"size"` // page size
}
