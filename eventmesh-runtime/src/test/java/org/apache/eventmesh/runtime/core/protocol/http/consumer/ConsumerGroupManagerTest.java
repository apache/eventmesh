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

package org.apache.eventmesh.runtime.core.protocol.http.consumer;

import org.apache.eventmesh.common.protocol.SubscriptionItem;
import org.apache.eventmesh.common.protocol.SubscriptionMode;
import org.apache.eventmesh.common.protocol.SubscriptionType;
import org.apache.eventmesh.runtime.boot.EventMeshHTTPServer;
import org.apache.eventmesh.runtime.core.consumergroup.ConsumerGroupConf;
import org.apache.eventmesh.runtime.core.consumergroup.ConsumerGroupTopicConf;

import java.util.Collections;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;

public class ConsumerGroupManagerTest {

    @Test
    public void testRefreshRemovesOnlyUnsubscribedTopicWithoutRestartingStorage() throws Exception {
        try (MockedConstruction<EventMeshConsumer> construction = Mockito.mockConstruction(EventMeshConsumer.class)) {
            ConsumerGroupManager manager = startedManager(config("orders", "payments"));
            EventMeshConsumer consumer = construction.constructed().get(0);
            Mockito.clearInvocations(consumer);
            ConsumerGroupConf remaining = config("payments");

            manager.refresh(remaining);

            Mockito.verify(consumer).unsubscribe("orders", SubscriptionMode.CLUSTERING);
            Mockito.verify(consumer).setConsumerGroupConf(remaining);
            Mockito.verifyNoMoreInteractions(consumer);
            Assertions.assertSame(remaining, manager.getConsumerGroupConfig());
        }
    }

    @Test
    public void testRefreshAddsNewTopicWithoutRestartingExistingSubscriptions() throws Exception {
        try (MockedConstruction<EventMeshConsumer> construction = Mockito.mockConstruction(EventMeshConsumer.class)) {
            final ConsumerGroupManager manager = startedManager(config("orders"));
            EventMeshConsumer consumer = construction.constructed().get(0);
            Mockito.clearInvocations(consumer);
            ConsumerGroupConf expanded = config("orders", "payments");

            manager.refresh(expanded);

            InOrder order = Mockito.inOrder(consumer);
            order.verify(consumer).setConsumerGroupConf(expanded);
            order.verify(consumer).subscribe("payments", expanded.getConsumerGroupTopicConf().get("payments").getSubscriptionItem());
            Mockito.verifyNoMoreInteractions(consumer);
        }
    }

    @Test
    public void testRefreshMovesTopicBetweenDeliveryModes() throws Exception {
        try (MockedConstruction<EventMeshConsumer> construction = Mockito.mockConstruction(EventMeshConsumer.class)) {
            final ConsumerGroupManager manager = startedManager(config("orders"));
            EventMeshConsumer consumer = construction.constructed().get(0);
            Mockito.clearInvocations(consumer);
            ConsumerGroupConf changed = config("orders");
            SubscriptionItem broadcast = new SubscriptionItem("orders", SubscriptionMode.BROADCASTING, SubscriptionType.ASYNC);
            changed.getConsumerGroupTopicConf().get("orders").setSubscriptionItem(broadcast);

            manager.refresh(changed);

            InOrder order = Mockito.inOrder(consumer);
            order.verify(consumer).unsubscribe("orders", SubscriptionMode.CLUSTERING);
            order.verify(consumer).setConsumerGroupConf(changed);
            order.verify(consumer).subscribe("orders", broadcast);
            Mockito.verifyNoMoreInteractions(consumer);
        }
    }

    @Test
    public void testRefreshUpdatesCallbackRoutingWithoutChangingBrokerSubscriptions() throws Exception {
        try (MockedConstruction<EventMeshConsumer> construction = Mockito.mockConstruction(EventMeshConsumer.class)) {
            final ConsumerGroupManager manager = startedManager(config("orders"));
            EventMeshConsumer consumer = construction.constructed().get(0);
            Mockito.clearInvocations(consumer);
            ConsumerGroupConf changed = config("orders");
            ConsumerGroupTopicConf topic = changed.getConsumerGroupTopicConf().get("orders");
            topic.setUrls(Collections.singleton("http://localhost:8081/events"));
            topic.setIdcUrls(Collections.singletonMap("idc", Collections.singletonList("http://localhost:8081/events")));

            manager.refresh(changed);

            Mockito.verify(consumer).setConsumerGroupConf(changed);
            Mockito.verifyNoMoreInteractions(consumer);
            Assertions.assertSame(changed, manager.getConsumerGroupConfig());
        }
    }

    private ConsumerGroupManager startedManager(ConsumerGroupConf configuration) throws Exception {
        ConsumerGroupManager manager = new ConsumerGroupManager(Mockito.mock(EventMeshHTTPServer.class), configuration);
        manager.init();
        manager.start();
        return manager;
    }

    private ConsumerGroupConf config(String... topics) {
        ConsumerGroupConf group = new ConsumerGroupConf("group");
        for (String name : topics) {
            ConsumerGroupTopicConf topic = new ConsumerGroupTopicConf();
            topic.setConsumerGroup("group");
            topic.setTopic(name);
            topic.setSubscriptionItem(new SubscriptionItem(name, SubscriptionMode.CLUSTERING, SubscriptionType.ASYNC));
            topic.setUrls(Collections.singleton("http://localhost:8080/events"));
            topic.setIdcUrls(Collections.singletonMap("idc", Collections.singletonList("http://localhost:8080/events")));
            group.getConsumerGroupTopicConf().put(name, topic);
        }
        return group;
    }
}
