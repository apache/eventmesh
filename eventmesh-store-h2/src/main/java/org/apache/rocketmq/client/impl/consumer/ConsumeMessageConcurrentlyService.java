/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.rocketmq.client.impl.consumer;

import java.util.List;

import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.common.protocol.body.ConsumeMessageDirectlyResult;

public class ConsumeMessageConcurrentlyService implements ConsumeMessageService {

	@Override
	public void start() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void shutdown(long awaitTerminateMillis) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void updateCorePoolSize(int corePoolSize) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void incCorePoolSize() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void decCorePoolSize() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public int getCorePoolSize() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public ConsumeMessageDirectlyResult consumeMessageDirectly(MessageExt msg, String brokerName) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void submitConsumeRequest(List<MessageExt> msgs, ProcessQueue processQueue, MessageQueue messageQueue,
			boolean dispathToConsume) {
		// TODO Auto-generated method stub
		
	}

}