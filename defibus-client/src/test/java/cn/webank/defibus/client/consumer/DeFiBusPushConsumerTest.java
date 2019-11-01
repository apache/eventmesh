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

package cn.webank.defibus.client.consumer;

import cn.webank.defibus.consumer.DeFiBusPushConsumer;
import org.apache.rocketmq.client.exception.MQClientException;
import org.junit.Test;

import static junit.framework.TestCase.assertFalse;
import static org.junit.Assert.assertTrue;

public class DeFiBusPushConsumerTest {
    private String topic = "FooBar";

    @Test
    public void test_unsubscribe() throws MQClientException {
        DeFiBusPushConsumer pushConsumer = new DeFiBusPushConsumer();
        pushConsumer.subscribe(topic);
        assertTrue(pushConsumer.getDefaultMQPushConsumer().getDefaultMQPushConsumerImpl().getRebalanceImpl().getSubscriptionInner().containsKey(topic));

        pushConsumer.unsubscribe(topic);
        assertFalse(pushConsumer.getDefaultMQPushConsumer().getDefaultMQPushConsumerImpl().getRebalanceImpl().getSubscriptionInner().containsKey(topic));
    }
}
