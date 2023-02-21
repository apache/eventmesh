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

package convert

import (
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/plugin/connector/rocketmq/constants"
	"github.com/apache/rocketmq-client-go/v2/primitive"
	"strings"
)

// TransferMessageSystemProperties re-set message system properties to fix pattern
func TransferMessageSystemProperties(message *primitive.Message) {
	for propertyKey := range constants.RocketMQMessageProperties.Iter() {
		if val := message.GetProperty(propertyKey); len(val) != 0 {
			key := strings.ReplaceAll(strings.ToLower(propertyKey), "_", constants.MessagePropertySeparator)
			message.WithProperty(key, val)
			message.RemoveProperty(propertyKey)
		}
	}
}
