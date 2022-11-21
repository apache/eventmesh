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

package filter

import (
	"encoding/json"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/log"
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/internal/dal/model"
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/third_party/jqer"
)

func FilterWorkflowTaskInputData(task *model.WorkflowTaskInstance) {
	if task == nil || len(task.Input) == 0 || len(task.Task.TaskInputFilter) == 0 {
		return
	}
	inputAfterFilter, err := filterJsonData(task.Task.TaskInputFilter, task.Input)
	if err != nil {
		log.Errorf("fail to filter task input data taskId=%s, err=%v", task.TaskID, err)
		return
	}
	task.Input = inputAfterFilter
}

func filterJsonData(filterJson string, inputDataJson string) (string, error) {
	var jsonObj interface{}
	err := json.Unmarshal([]byte(inputDataJson), &jsonObj)
	if err != nil {
		return "", err
	}
	jq := jqer.NewJQ()
	ret, err := jq.Object(jsonObj, filterJson)
	if err != nil {
		return "", err
	}
	outputDataJson, err := json.Marshal(ret)
	if err != nil {
		return "", err
	}
	return string(outputDataJson), nil
}
