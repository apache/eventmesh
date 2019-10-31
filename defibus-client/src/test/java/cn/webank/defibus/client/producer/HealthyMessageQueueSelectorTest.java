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

package cn.webank.defibus.client.producer;

import cn.webank.defibus.client.impl.producer.DeFiBusProducerImpl;
import cn.webank.defibus.client.impl.producer.HealthyMessageQueueSelector;
import cn.webank.defibus.client.impl.producer.MessageQueueHealthManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageQueue;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

public class HealthyMessageQueueSelectorTest {
    @Test
    public void testLocalValidQueue() {

        final DeFiBusProducerImpl producerImplMock = Mockito.mock(DeFiBusProducerImpl.class);
        Message msg = new Message();
        msg.setTopic("testtopic");

        Set<String> locBrokers = new HashSet<>();
        locBrokers.add("localIDC");
//        PowerMockito.when(producerImplMock.getLocalBrokers()).thenReturn(locBrokers);

        MessageQueueHealthManager manager = new MessageQueueHealthManager(60 * 1000);
        HealthyMessageQueueSelector selector = new HealthyMessageQueueSelector(manager);
        selector.setLocalBrokers(locBrokers);

        List<MessageQueue> mqs = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            MessageQueue m = new MessageQueue("testtopic", "localIDC", i);
            mqs.add(m);
        }

        final AtomicReference<MessageQueue> selectedResultRef = new AtomicReference<MessageQueue>();
        MessageQueue select = selector.select(mqs, msg, selectedResultRef);
        Assert.assertTrue(select != null && !(select.getBrokerName().equalsIgnoreCase("dummyBroker")));
    }

    @Test
    public void testErrorQueue() {
        Set<String> locBrokers = new HashSet<>();
        locBrokers.add("localIDC");

        MessageQueueHealthManager manager = new MessageQueueHealthManager(60 * 1000);
        HealthyMessageQueueSelector selector = new HealthyMessageQueueSelector(manager);
        selector.setLocalBrokers(locBrokers);

        List<MessageQueue> mqs = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            MessageQueue m = new MessageQueue("testtopic", "localIDC", i);
            mqs.add(m);
            manager.faultMap.put(m, System.currentTimeMillis() + 60 * 1000);//设置为熔断queue
        }

        MessageQueue m = new MessageQueue("testtopic", "dummyBroker", 0);
        mqs.add(m);

        final AtomicReference<MessageQueue> selectedResultRef = new AtomicReference<MessageQueue>();
        //List<MessageQueue> mqs, Message msg, final Object selectedResultRef
        Message msg = new Message();
        msg.setTopic("testtopic");
        MessageQueue select = selector.select(mqs, msg, selectedResultRef);
        Assert.assertTrue(select != null && select.getBrokerName().equalsIgnoreCase("dummyBroker"));
    }

    @Test
    public void testOtherValidQueue() {
        Set<String> locBrokers = new HashSet<>();
        locBrokers.add("localIDC");

        MessageQueueHealthManager manager = new MessageQueueHealthManager(60 * 1000);
        HealthyMessageQueueSelector selector = new HealthyMessageQueueSelector(manager);
        selector.setLocalBrokers(locBrokers);

        List<MessageQueue> mqs = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            MessageQueue m = new MessageQueue("testtopic", "localIDC1", i);
            mqs.add(m);
        }

        final AtomicReference<MessageQueue> selectedResultRef = new AtomicReference<MessageQueue>();
        //List<MessageQueue> mqs, Message msg, final Object selectedResultRef
        Message msg = new Message();
        msg.setTopic("testtopic");
        MessageQueue select = selector.select(mqs, msg, selectedResultRef);
        Assert.assertTrue(select != null && !(select.getBrokerName().equalsIgnoreCase("dummyBroker")));
    }

    @Test
    public void testBizTopic() {
        String bizTopic = "XX0-s-00000000-01-0";
        String localBrokerName = "localIdcBroker";
        String otherIdcBrokerName = "otherIdcBroker";

        Message msg = new Message();
        msg.setTopic(bizTopic);

        Set<String> localBrokers = new HashSet<>();
        localBrokers.add(localBrokerName);

        MessageQueueHealthManager manager = new MessageQueueHealthManager(60 * 1000);
        HealthyMessageQueueSelector selector = new HealthyMessageQueueSelector(manager);
        selector.setLocalBrokers(localBrokers);

        //construct mq data
        List<MessageQueue> localMqs = new ArrayList<>();
        List<MessageQueue> otherMqs = new ArrayList<>();
        List<MessageQueue> mqs = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            MessageQueue m = new MessageQueue(bizTopic, localBrokerName, i);
            mqs.add(m);
            localMqs.add(m);
        }
        for (int i = 0; i < 3; i++) {
            MessageQueue m = new MessageQueue(bizTopic, otherIdcBrokerName, i);
            mqs.add(m);
            otherMqs.add(m);
        }
        Collections.sort(mqs);

        //case 1:There are mqs that aren't isolated in this IDC, select from this IDC
        for (int i = 0; i < mqs.size(); i++) {
            final AtomicReference<MessageQueue> selectedResultRef = new AtomicReference<MessageQueue>();
            MessageQueue selectResult = selector.select(mqs, msg, selectedResultRef);

            Assert.assertTrue(selectResult != null
                && localBrokers.contains(selectResult.getBrokerName()));
            Assert.assertTrue(!selector.getMessageQueueHealthManager().faultMap.containsKey(selectResult));
        }

        //case 2:All mqs in this IDC are isolated, select mq from other IDC.
        for (MessageQueue mq : localMqs) {
            selector.getMessageQueueHealthManager().markQueueFault(mq);
        }
        for (int i = 0; i < mqs.size(); i++) {
            final AtomicReference<MessageQueue> selectedResultRef = new AtomicReference<MessageQueue>();
            MessageQueue selectResult = selector.select(mqs, msg, selectedResultRef);

            Assert.assertTrue(selectResult != null
                && !localBrokers.contains(selectResult.getBrokerName()));
            Assert.assertTrue(!selector.getMessageQueueHealthManager().faultMap.containsKey(selectResult));
        }

        //case 3:All mqs are isolated, select one randomly
        for (MessageQueue mq : otherMqs) {
            selector.getMessageQueueHealthManager().markQueueFault(mq);
        }
        for (int i = 0; i < mqs.size(); i++) {
            final AtomicReference<MessageQueue> selectedResultRef = new AtomicReference<MessageQueue>();
            MessageQueue selectResult = selector.select(mqs, msg, selectedResultRef);

            Assert.assertTrue(selectResult != null
                && (selectResult.getBrokerName().equals(localBrokerName) || selectResult.getBrokerName().equals(otherIdcBrokerName)));
            Assert.assertTrue(selector.getMessageQueueHealthManager().faultMap.containsKey(selectResult));
        }
    }

    @Test
    public void testRetryBizTopic() {
        String bizTopic = "XX0-s-00000000-01-0";
        String localBrokerNamePrefix = "localIdcBroker";
        String otherIdcBrokerNamePrefix = "otherIdcBroker";

        Message msg = new Message();
        msg.setTopic(bizTopic);

        Set<String> localBrokers = new HashSet<>();
        for (int i = 1; i <= 3; i++) {
            localBrokers.add(localBrokerNamePrefix + i);
        }

        MessageQueueHealthManager manager = new MessageQueueHealthManager(60 * 1000);
        HealthyMessageQueueSelector selector = new HealthyMessageQueueSelector(manager);
        selector.setLocalBrokers(localBrokers);

        //construct mq data
        List<MessageQueue> localMqs = new ArrayList<>();
        List<MessageQueue> otherMqs = new ArrayList<>();
        List<MessageQueue> mqs = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            for (int j = 1; j < 3; j++) {
                MessageQueue m = new MessageQueue(bizTopic, localBrokerNamePrefix + j, i);
                mqs.add(m);
                localMqs.add(m);
            }
        }
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j <= 3; j++) {
                MessageQueue m = new MessageQueue(bizTopic, otherIdcBrokerNamePrefix + j, i);
                mqs.add(m);
                otherMqs.add(m);
            }
        }
        Collections.sort(mqs);

        //case 1:There are mqs that aren't isolated in this IDC, select from this IDC
        MessageQueue lastSelectedMq = localMqs.get(0);
        for (int i = 0; i < mqs.size(); i++) {
            final AtomicReference<MessageQueue> selectedResultRef = new AtomicReference<MessageQueue>();
            selectedResultRef.set(lastSelectedMq);
            MessageQueue selectResult = selector.select(mqs, msg, selectedResultRef);

            Assert.assertTrue(selectResult != null
                && localBrokers.contains(selectResult.getBrokerName())
                && !selectResult.getBrokerName().equals(lastSelectedMq.getBrokerName()));
            Assert.assertTrue(!selector.getMessageQueueHealthManager().faultMap.containsKey(selectResult));
        }

        //case 2:All mqs in this IDC are isolated, select mq from other IDC.
        for (MessageQueue mq : localMqs) {
            selector.getMessageQueueHealthManager().markQueueFault(mq);
        }
        for (int i = 0; i < mqs.size(); i++) {
            final AtomicReference<MessageQueue> selectedResultRef = new AtomicReference<MessageQueue>();
            selectedResultRef.set(lastSelectedMq);
            MessageQueue selectResult = selector.select(mqs, msg, selectedResultRef);

            Assert.assertTrue(selectResult != null
                && !localBrokers.contains(selectResult.getBrokerName())
                && !selectResult.getBrokerName().equals(lastSelectedMq.getBrokerName()));
            Assert.assertTrue(!selector.getMessageQueueHealthManager().faultMap.containsKey(selectResult));
        }

        //case 3:All mqs are isolated, select one randomly
        for (MessageQueue mq : otherMqs) {
            selector.getMessageQueueHealthManager().markQueueFault(mq);
        }
        for (int i = 0; i < mqs.size(); i++) {
            final AtomicReference<MessageQueue> selectedResultRef = new AtomicReference<MessageQueue>();
            selectedResultRef.set(lastSelectedMq);
            MessageQueue selectResult = selector.select(mqs, msg, selectedResultRef);

            Assert.assertTrue(selectResult != null
                && !selectResult.getBrokerName().equals(lastSelectedMq.getBrokerName()));
            Assert.assertTrue(selector.getMessageQueueHealthManager().faultMap.containsKey(selectResult));
        }
    }
}
