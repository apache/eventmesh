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

package org.apache.eventmesh.runtime.core.consumer;

import org.apache.eventmesh.common.protocol.SubscriptionItem;
import org.apache.eventmesh.common.protocol.SubscriptionMode;
import org.apache.eventmesh.common.protocol.SubscriptionType;
import org.apache.eventmesh.runtime.core.consumergroup.ConsumerGroupConf;
import org.apache.eventmesh.runtime.core.consumergroup.ConsumerGroupTopicConf;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class SubscriptionManagerTest {

    private static final String URL = "http://localhost:8080/events";

    private final SubscriptionManager manager = new SubscriptionManager(false, null);

    @Test
    public void testUnsubscribePreservesOtherTopics() {
        subscribe("group", "1", "idc", URL, "orders", "payments");
        ConsumerGroupConf remaining = manager.removeSubscription("group", "1", URL, Collections.singletonList("orders"));
        Assertions.assertEquals(Collections.singleton("payments"), remaining.getConsumerGroupTopicConf().keySet());
        Assertions.assertFalse(manager.getLocalClientInfoMapping().containsKey("group@orders"));
        Assertions.assertTrue(manager.getLocalClientInfoMapping().containsKey("group@payments"));
    }

    @Test
    public void testUnsubscribeProcessesEveryRequestedTopicAndLeavesOtherGroups() {
        subscribe("group", "1", "idc", URL, "orders", "payments");
        subscribe("other-group", "1", "idc", URL, "orders");
        Assertions.assertNull(manager.removeSubscription("group", "1", URL, Arrays.asList("orders", "payments")));
        Assertions.assertFalse(manager.getLocalConsumerGroupMapping().containsKey("group"));
        Assertions.assertEquals(Collections.singleton("other-group@orders"), manager.getLocalClientInfoMapping().keySet());
        Assertions.assertTrue(manager.getLocalConsumerGroupMapping().containsKey("other-group"));
    }

    @Test
    public void testUnsubscribePreservesSameUrlRegisteredByAnotherProcess() {
        subscribe("group", "1", "idc", URL, "orders");
        subscribe("group", "2", "idc", URL, "orders");
        ConsumerGroupConf remaining = manager.removeSubscription("group", "1", URL, Collections.singletonList("orders"));
        ConsumerGroupTopicConf topic = remaining.getConsumerGroupTopicConf().get("orders");
        Assertions.assertEquals(Collections.singleton(URL), topic.getUrls());
        Assertions.assertEquals(Collections.singletonList(URL), topic.getIdcUrls().get("idc"));
        Assertions.assertEquals("2", manager.getLocalClientInfoMapping().get("group@orders").get(0).getPid());
    }

    @Test
    public void testUnsubscribeRebuildsRemainingIdcUrls() {
        String otherUrl = "http://localhost:8081/events";
        subscribe("group", "1", "idc-a", URL, "orders");
        subscribe("group", "2", "idc-b", otherUrl, "orders");
        ConsumerGroupConf remaining = manager.removeSubscription("group", "1", URL, Collections.singletonList("orders"));
        ConsumerGroupTopicConf topic = remaining.getConsumerGroupTopicConf().get("orders");
        Assertions.assertEquals(Collections.singleton(otherUrl), topic.getUrls());
        Assertions.assertEquals(Collections.singletonMap("idc-b", Collections.singletonList(otherUrl)), topic.getIdcUrls());
    }

    @Test
    public void testUnknownSubscriptionDoesNotCreateClientOrRemoveExistingGroup() {
        subscribe("group", "1", "idc", URL, "orders");
        ConsumerGroupConf remaining = manager.removeSubscription("group", "2", URL, Arrays.asList("orders", "unknown"));
        Assertions.assertEquals(Collections.singleton("orders"), remaining.getConsumerGroupTopicConf().keySet());
        Assertions.assertEquals(1, manager.getLocalClientInfoMapping().size());
        Assertions.assertEquals("1", manager.getLocalClientInfoMapping().get("group@orders").get(0).getPid());
        Assertions.assertNull(manager.removeSubscription("unknown", "1", URL, Collections.singletonList("orders")));
        Assertions.assertEquals(1, manager.getLocalClientInfoMapping().size());
    }

    private void subscribe(String group, String pid, String idc, String url, String... topics) {
        ClientInfo client = new ClientInfo();
        client.setPid(pid);
        client.setIdc(idc);
        List<SubscriptionItem> subscriptions = Arrays.stream(topics)
            .map(topic -> new SubscriptionItem(topic, SubscriptionMode.CLUSTERING, SubscriptionType.ASYNC))
            .collect(Collectors.toList());
        manager.registerClient(client, group, subscriptions, url);
        manager.updateSubscription(client, group, url, subscriptions);
    }
}
