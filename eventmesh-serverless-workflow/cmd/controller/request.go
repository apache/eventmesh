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

package main

import "github.com/apache/incubator-eventmesh/eventmesh-workflow-go/internal/dal/model"

// SaveWorkflowRequest save workflow request body
type SaveWorkflowRequest struct {
	Workflow model.Workflow `json:"workflow"`
}

// QueryWorkflowsRequest query workflow list request body
type QueryWorkflowsRequest struct {
	WorkflowID string `form:"workflow_id" json:"workflow_id"`
	Status     int    `form:"status" json:"status"`
	Page       int    `form:"page" json:"page"` // page num
	Size       int    `form:"size" json:"size"` // page size
}

// QueryWorkflowInstancesRequest query workflow instances request body
type QueryWorkflowInstancesRequest struct {
	WorkflowID string `form:"workflow_id" json:"workflow_id"`
	Page       int    `form:"page" json:"page"` // page num
	Size       int    `form:"size" json:"size"` // page size
}
