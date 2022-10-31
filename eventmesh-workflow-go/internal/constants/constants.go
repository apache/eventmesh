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

package constants

type TaskType int
type QueueType int

const (
	// QueueTypeInMemory in memory queue type
	QueueTypeInMemory = "in-memory"
)

const (
	TaskStartID  = "START"
	TaskEndID    = "END"
	NormalStatus = 1
)

// task instance status
const (
	TaskInstanceSleepStatus   = 1
	TaskInstanceWaitStatus    = 2
	TaskInstanceProcessStatus = 3
	TaskInstanceSuccessStatus = 4
	TaskInstanceFailStatus    = 5
)

// workflow instance status
const (
	WorkflowInstanceProcessStatus = 1
	WorkflowInstanceSuccessStatus = 2
)

// log name
const (
	LogSchedule = "schedule"
	LogQueue    = "queue"
)

const (
	TaskTypeOperation = "operation"
	TaskTypeEvent     = "event"
	TaskTypeSwitch    = "switch"
)

const (
	// RetryAttempts fail retry max times
	RetryAttempts = 5
)

const (
	EventTypePublish                 = "publish"
	EventPropsWorkflowInstanceID     = "workflowinstanceid"
	EventPropsWorkflowTaskInstanceID = "workflowtaskinstanceid"
)
