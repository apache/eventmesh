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
import org.apache.eventmesh.runtime.ingress.UniIngressService;
import org.apache.eventmesh.runtime.push.BufferedEvent;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import io.cloudevents.CloudEvent;

import lombok.extern.slf4j.Slf4j;

/**
 * Bridges the A2A (Agent-to-Agent) protocol onto the EventMesh Runtime's CloudEvents-over-MQ core.
 * Implements {@link A2AMessageTransport} by mapping:
 * <ul>
 *   <li>{@code publish} → {@code UniIngressService.publish} (CloudEvent → MQ)</li>
 *   <li>{@code subscribe} → registers a poll loop on the Runtime's push buffer, driving the A2A
 *       callback when events arrive.</li>
 * </ul>
 *
 * <p>So A2A agents talk through EventMesh as their message bus — no separate transport. The A2A
 * protocol semantics (AgentCard, task lifecycle, SSE streaming) live in the A2A client; this class
 * is just the wire layer.</p>
 */
@Slf4j
public class EventMeshA2ATransport implements A2AMessageTransport {

    private final UniIngressService ingress;
    private final String clientId;
    private final ScheduledExecutorService pollExecutor;
    private final AtomicBoolean polling = new AtomicBoolean(false);

    /** topic → callback */
    private final ConcurrentHashMap<String, MessageCallback> callbacks = new ConcurrentHashMap<>();

    public EventMeshA2ATransport(UniIngressService ingress, String clientId) {
        this.ingress = ingress;
        this.clientId = clientId;
        this.pollExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "a2a-transport-poll");
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    public void publish(String topic, CloudEvent event) throws Exception {
        ingress.publish(topic, event).get(10, TimeUnit.SECONDS);
    }

    @Override
    public String subscribe(String topicPattern, MessageCallback callback) throws Exception {
        // Subscribe via ingress (BROADCAST — each agent gets every message on the topic)
        ingress.subscribe(topicPattern, clientId, org.apache.eventmesh.runtime.subscription.DistributionMode.BROADCAST, null);
        callbacks.put(topicPattern, callback);
        startPollLoop();
        return "a2a-sub-" + topicPattern;
    }

    @Override
    public void unsubscribe(String subscriptionId) throws Exception {
        String topic = subscriptionId.replace("a2a-sub-", "");
        callbacks.remove(topic);
        ingress.getSubscriptionManager().unsubscribeByClient(clientId);
        if (callbacks.isEmpty()) {
            polling.set(false);
        }
    }

    private void startPollLoop() {
        if (!polling.compareAndSet(false, true)) {
            return;
        }
        pollExecutor.scheduleWithFixedDelay(() -> {
            if (!polling.get()) {
                return;
            }
            try {
                List<BufferedEvent> batch = ingress.poll(clientId, 100, 500L);
                for (BufferedEvent be : batch) {
                    // Egress boundary: the push buffer carries Frame; convert to CloudEvent for the A2A callback.
                    CloudEvent event = be.getEvent().toCloudEvent();
                    String topic = event.getSubject() != null ? event.getSubject() : "default";
                    MessageCallback cb = callbacks.get(topic);
                    if (cb != null) {
                        cb.onMessage(topic, event);
                    } else {
                        // Try all callbacks (wildcard topic patterns)
                        for (var entry : callbacks.entrySet()) {
                            if (topic.matches(entry.getKey().replace("*", ".*"))) {
                                entry.getValue().onMessage(topic, event);
                                break;
                            }
                        }
                    }
                    ingress.ack(be.getDeliveryId());
                }
            } catch (Exception e) {
                log.debug("A2A poll loop: {}", e.toString());
            }
        }, 100, 100, TimeUnit.MILLISECONDS);
    }

    public void shutdown() {
        polling.set(false);
        pollExecutor.shutdownNow();
    }
}
