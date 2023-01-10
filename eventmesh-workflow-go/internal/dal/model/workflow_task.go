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

type WorkflowTask struct {
	ID                 int                     `json:"id" gorm:"column:id;type:int;primaryKey;autoIncrement"`
	WorkflowID         string                  `json:"workflow_id" gorm:"column:workflow_id;type:varchar;size:1024"`
	TaskID             string                  `json:"task_id" gorm:"column:task_id;type:varchar;size:1024"`
	TaskName           string                  `json:"task_name" gorm:"column:task_name;type:varchar;size:1024"`
	TaskType           string                  `json:"task_type" gorm:"column:task_type;type:varchar;size:64"`
	TaskInputFilter    string                  `json:"task_input_filter" gorm:"column:task_input_filter;type:varchar;size:1024"`
	Status             int                     `json:"status" gorm:"column:status;type:int"`
	CreateTime         time.Time               `json:"create_time"`
	UpdateTime         time.Time               `json:"update_time"`
	Actions            []*WorkflowTaskAction   `json:"-" gorm:"-"`
	TaskIDs            []string                `json:"-" gorm:"-"`
	WorkflowInstanceID string                  `json:"workflow_instance_id" gorm:"-"`
	ChildTasks         []*WorkflowTaskRelation `json:"-" gorm:"-"`
}

func (w WorkflowTask) TableName() string {
	return "t_workflow_task"
}

type WorkflowTaskAction struct {
	ID            int       `json:"id" gorm:"column:id;type:int;primaryKey;autoIncrement"`
	WorkflowID    string    `json:"workflow_id" gorm:"column:workflow_id;type:varchar;size:1024"`
	TaskID        string    `json:"task_id" gorm:"column:task_id;type:varchar;size:1024"`
	OperationName string    `json:"operation_name" gorm:"column:operation_name;type:varchar;size:1024"`
	OperationType string    `json:"operation_type" gorm:"column:operation_type;type:varchar;size:1024"`
	Status        int       `json:"status" gorm:"column:status;type:int"`
	CreateTime    time.Time `json:"create_time"`
	UpdateTime    time.Time `json:"Update_time"`
	TaskIDs       []string  `json:"-" gorm:"-"`
}

func (w WorkflowTaskAction) TableName() string {
	return "t_workflow_task_action"
}

type WorkflowTaskRelation struct {
	ID         int       `json:"id" gorm:"column:id;type:int;primaryKey;autoIncrement"`
	WorkflowID string    `json:"workflow_id" gorm:"column:workflow_id;type:varchar;size:1024"`
	FromTaskID string    `json:"from_task_id" gorm:"column:from_task_id;type:varchar;size:1024"`
	ToTaskID   string    `json:"to_task_id" gorm:"column:to_task_id;type:varchar;size:1024"` // DSL transition task id
	Condition  string    `json:"condition" gorm:"column:condition;type:varchar;size:2048"`   // DSL transition condition
	Status     int       `json:"status" gorm:"column:status;type:int"`
	CreateTime time.Time `json:"create_time"`
	UpdateTime time.Time `json:"update_time"`
}

func (w WorkflowTaskRelation) TableName() string {
	return "t_workflow_task_relation"
}

type WorkflowTaskInstance struct {
	ID                 int           `json:"id" gorm:"column:id;type:int;primaryKey;autoIncrement"`
	WorkflowInstanceID string        `json:"workflow_instance_id" gorm:"column:workflow_instance_id;type:varchar;size:1024"`
	WorkflowID         string        `json:"workflow_id" gorm:"column:workflow_id;type:varchar;size:1024"`
	TaskID             string        `json:"task_id" gorm:"column:task_id;type:varchar;size:1024"`
	TaskInstanceID     string        `json:"task_instance_id" gorm:"column:task_instance_id;type:varchar;size:1024"`
	Status             int           `json:"status" gorm:"column:status;type:int"`
	Input              string        `json:"input" gorm:"column:input;type:text;"`
	RetryTimes         int           `json:"retry_times" gorm:"column:retry_times;type:int"`
	CreateTime         time.Time     `json:"create_time" gorm:"column:create_time"`
	UpdateTime         time.Time     `json:"update_time" gorm:"column:update_time"`
	Task               *WorkflowTask `json:"task" gorm:"-"`
	Order              string        `json:"order" gorm:"-"`
	IsStart            bool          `json:"is_start" gorm:"-"`
}

func (w WorkflowTaskInstance) TableName() string {
	return "t_workflow_task_instance"
}
