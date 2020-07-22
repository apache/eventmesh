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

import org.apache.rocketmq.common.message.MessageQueue;
import org.junit.Test;
import org.mockito.Spy;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MessageQueueHealthManagerTest {
    private long isoTime = 3 * 1000L;
    @Spy
    private MessageQueueHealthManager messageQueueHealthManager = new MessageQueueHealthManager(isoTime);

    @Test
    public void testMarkQueueFault() throws Exception {
        MessageQueue mq = new MessageQueue("topic1", "brokerName1", 0);
        messageQueueHealthManager.markQueueFault(mq);
        assertTrue(messageQueueHealthManager.isQueueFault(mq));
        Thread.sleep(isoTime + 10);
        assertFalse(messageQueueHealthManager.isQueueFault(mq));
    }

    @Test
    public void testMarkQueueHealthy() throws Exception {
        MessageQueue mq = new MessageQueue("topic1", "brokerName1", 0);
        messageQueueHealthManager.markQueueFault(mq);
        assertTrue(messageQueueHealthManager.isQueueFault(mq));
        messageQueueHealthManager.markQueueHealthy(mq);
        assertTrue(messageQueueHealthManager.isQueueHealthy(mq));
    }

}
