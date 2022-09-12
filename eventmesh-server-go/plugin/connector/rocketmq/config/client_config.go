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

package config

// ClientConfig config of RocketMQ client
type ClientConfig struct {
	// AccessPoints addresses of rocketmq name servers, join by ";"
	AccessPoints string `json:"access_points"`
	// Namespace client namespace
	Namespace string `json:"namespace"`
	// InstanceName client instance name
	InstanceName string `json:"instance_name"`
	// ProducerGroupName producer client group name
	ProducerGroupName string `json:"group_name"`
	// SendMsgTimeout producer send message timeout (ms), default 3000ms
	SendMsgTimeout string `json:"send_msg_timeout"`
	// ProducerRetryTimes producer retry time
	ProducerRetryTimes string `json:"producer_retry_times"`
	// CompressMsgBodyThreshold producer compress message body threshold
	CompressMsgBodyThreshold string `json:"compress_msg_body_threshold"`
}
