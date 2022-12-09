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

import (
	gs "github.com/deckarep/golang-set/v2"
)

// RocketMQMessageProperties Properties of RocketMQ Message
var RocketMQMessageProperties gs.Set[string]

func init() {
	RocketMQMessageProperties = gs.NewSet[string]()
	RocketMQMessageProperties.Add(" ")
	RocketMQMessageProperties.Add("KEYS")
	RocketMQMessageProperties.Add("TAGS")
	RocketMQMessageProperties.Add("WAIT")
	RocketMQMessageProperties.Add("DELAY")
	RocketMQMessageProperties.Add("RETRY_TOPIC")
	RocketMQMessageProperties.Add("REAL_TOPIC")
	RocketMQMessageProperties.Add("REAL_QID")
	RocketMQMessageProperties.Add("TRAN_MSG")
	RocketMQMessageProperties.Add("PGROUP")
	RocketMQMessageProperties.Add("MIN_OFFSET")
	RocketMQMessageProperties.Add("MAX_OFFSET")
	RocketMQMessageProperties.Add("BUYER_ID")
	RocketMQMessageProperties.Add("ORIGIN_MESSAGE_ID")
	RocketMQMessageProperties.Add("TRANSFER_FLAG")
	RocketMQMessageProperties.Add("CORRECTION_FLAG")
	RocketMQMessageProperties.Add("MQ2_FLAG")
	RocketMQMessageProperties.Add("RECONSUME_TIME")
	RocketMQMessageProperties.Add("MSG_REGION")
	RocketMQMessageProperties.Add("TRACE_ON")
	RocketMQMessageProperties.Add("UNIQ_KEY")
	RocketMQMessageProperties.Add("MAX_RECONSUME_TIMES")
	RocketMQMessageProperties.Add("CONSUME_START_TIME")
	RocketMQMessageProperties.Add("TRAN_PREPARED_QUEUE_OFFSET")
	RocketMQMessageProperties.Add("TRANSACTION_CHECK_TIMES")
	RocketMQMessageProperties.Add("CHECK_IMMUNITY_TIME_IN_SECONDS")
	RocketMQMessageProperties.Add("SHARDING_KEY")
	RocketMQMessageProperties.Add("__transactionId__")
	RocketMQMessageProperties.Add("CORRELATION_ID")
	RocketMQMessageProperties.Add("REPLY_TO_CLIENT")
	RocketMQMessageProperties.Add("TTL")
	RocketMQMessageProperties.Add("ARRIVE_TIME")
	RocketMQMessageProperties.Add("MSG_TYPE")
	RocketMQMessageProperties.Add("CLUSTER")
}
