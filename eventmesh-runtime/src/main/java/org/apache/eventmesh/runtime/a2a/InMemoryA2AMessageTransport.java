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

package org.apache.eventmesh.runtime.a2a;

import org.apache.eventmesh.protocol.a2a.A2AMessageTransport;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.cloudevents.CloudEvent;

/**
 * Simple in-memory implementation of {@link A2AMessageTransport} for testing and PoC.
 * Messages are delivered directly to subscribers in the same JVM.
 */
public class InMemoryA2AMessageTransport implements A2AMessageTransport {

    private static final Logger log = LoggerFactory.getLogger(InMemoryA2AMessageTransport.class);

    private final ConcurrentHashMap<String, Subscription> subscriptions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<Subscription>> topicToSubscriptions = new ConcurrentHashMap<>();

    @Override
    public void publish(String topic, CloudEvent event) throws Exception {
        log.debug("InMemory publish to topic: {}", topic);
        int delivered = 0;
        for (ConcurrentHashMap.Entry<String, List<Subscription>> entry : topicToSubscriptions.entrySet()) {
            String pattern = entry.getKey();
            if (matchesWildcard(pattern, topic)) {
                for (Subscription sub : entry.getValue()) {
                    sub.callback.onMessage(topic, event);
                    delivered++;
                }
            }
        }
        if (delivered == 0) {
            log.debug("No subscribers matched topic: {}", topic);
        }
    }

    @Override
    public String subscribe(String topicPattern, MessageCallback callback) throws Exception {
        String subId = UUID.randomUUID().toString();
        Subscription sub = new Subscription(subId, topicPattern, callback);
        subscriptions.put(subId, sub);
        topicToSubscriptions.computeIfAbsent(topicPattern, k -> new CopyOnWriteArrayList<>()).add(sub);
        log.info("InMemory subscribe: pattern={}, subId={}", topicPattern, subId);
        return subId;
    }

    @Override
    public void unsubscribe(String subscriptionId) throws Exception {
        Subscription sub = subscriptions.remove(subscriptionId);
        if (sub != null) {
            List<Subscription> subs = topicToSubscriptions.get(sub.topicPattern);
            if (subs != null) {
                subs.remove(sub);
                if (subs.isEmpty()) {
                    topicToSubscriptions.remove(sub.topicPattern);
                }
            }
            log.info("InMemory unsubscribe: subId={}", subscriptionId);
        }
    }

    /**
     * Simple wildcard matching supporting "+" (single-level).
     */
    private boolean matchesWildcard(String pattern, String topic) {
        if (pattern.equals(topic)) {
            return true;
        }
        String[] patternParts = pattern.split("/");
        String[] topicParts = topic.split("/");
        if (patternParts.length != topicParts.length) {
            return false;
        }
        for (int i = 0; i < patternParts.length; i++) {
            if (!"+".equals(patternParts[i]) && !patternParts[i].equals(topicParts[i])) {
                return false;
            }
        }
        return true;
    }

    private static class Subscription {

        final String id;
        final String topicPattern;
        final MessageCallback callback;

        Subscription(String id, String topicPattern, MessageCallback callback) {
            this.id = id;
            this.topicPattern = topicPattern;
            this.callback = callback;
        }
    }
}
