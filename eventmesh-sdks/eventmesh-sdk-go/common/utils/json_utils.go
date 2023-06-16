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

package utils

import (
	"encoding/json"

	"github.com/apache/eventmesh/eventmesh-sdk-go/log"
)

func MarshalJsonBytes(obj interface{}) []byte {
	ret, err := json.Marshal(obj)
	if err != nil {
		log.Fatalf("Failed to marshal json")
	}
	return ret
}

func MarshalJsonString(obj interface{}) string {
	return string(MarshalJsonBytes(obj))
}

func UnMarshalJsonBytes(data []byte, obj interface{}) {
	err := json.Unmarshal(data, obj)
	if err != nil {
		log.Fatalf("Failed to unmarshal json")
	}
}

func UnMarshalJsonString(data string, obj interface{}) {
	UnMarshalJsonBytes([]byte(data), obj)
}
