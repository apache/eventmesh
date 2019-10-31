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

package cn.webank.defibus.client.impl.producer;

import java.util.concurrent.ConcurrentHashMap;
import org.apache.rocketmq.common.message.MessageQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MessageQueueHealthManager {
    public static final Logger LOGGER = LoggerFactory.getLogger(MessageQueueHealthManager.class);

    public final ConcurrentHashMap<MessageQueue, Long/*isolate expire timestamp*/> faultMap = new ConcurrentHashMap<MessageQueue, Long>(32);

    /**
     * unhealthy mq isolate time in millisecond
     */
    private long isoTime = 60 * 1000L;

    public MessageQueueHealthManager(long isoTime) {
        this.isoTime = isoTime;
    }

    public void markQueueFault(MessageQueue mq) {
        faultMap.put(mq, System.currentTimeMillis() + isoTime);

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("mark queue {} fault for time {}", mq, isoTime);
        }
    }

    public void markQueueHealthy(MessageQueue mq) {
        if (faultMap.containsKey(mq)) {
            LOGGER.info("mark queue healthy. {}", mq);
            faultMap.remove(mq);
        }
    }

    public boolean isQueueFault(MessageQueue mq) {
        Long isolateUntilWhen = faultMap.get(mq);
        return isolateUntilWhen != null && System.currentTimeMillis() < isolateUntilWhen;
    }

    public boolean isQueueHealthy(MessageQueue mq) {
        return !isQueueFault(mq);
    }
}
