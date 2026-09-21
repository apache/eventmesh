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

package org.apache.eventmesh.agent;

import org.apache.eventmesh.agent.llm.OpenAiLlmClient;
import org.apache.eventmesh.client.cloudevents.CloudEventsClient;

import java.util.List;

import lombok.extern.slf4j.Slf4j;

/**
 * Agent process entry (v2). Boot sequence (§5.2): register with the runtime (get its agent-parent) →
 * subscribe its {@code agent.<agentId>} channel - flip READY (ready-before-route) - heartbeat loop.
 * All config via {@code -D} system properties.
 *
 * <p>Config keys: {@code agent.runtime.url}, {@code agent.id}, {@code agent.heartbeat.intervalMs},
 * {@code agent.capacity}, {@code agent.conversation.maxHistory}, {@code llm.base.url},
 * {@code llm.api.key}, {@code llm.model}.</p>
 */
@Slf4j
public class AgentApplication {

    public static void main(String[] args) throws Exception {
        String runtimeUrl = System.getProperty("agent.runtime.url", "http://localhost:10105");
        String agentId = System.getProperty("agent.id", "agent-" + Long.toString(System.currentTimeMillis(), 36));
        String llmBase = System.getProperty("llm.base.url", "https://api.openai.com");
        String llmKey = System.getProperty("llm.api.key", "");
        String llmModel = System.getProperty("llm.model", "gpt-4o-mini");
        final long heartbeatMs = Long.getLong("agent.heartbeat.intervalMs", 10_000L);
        int maxHistory = Integer.getInteger("agent.conversation.maxHistory", 20);
        int maxConversations = Integer.getInteger("agent.conversation.maxConversations", 1000);
        int capacity = Integer.getInteger("agent.capacity", 100);
        // #5405: fail fast on an empty LLM key — an agent that registers READY without a usable
        // key fails every routed request. Opt out explicitly for local/mock gateways.
        boolean llmKeyOptional = Boolean.parseBoolean(System.getProperty("llm.api.key.optional", "false"));
        if (llmKey.isEmpty() && !llmKeyOptional) {
            throw new IllegalStateException("llm.api.key is empty - set -Dllm.api.key=<key>"
                + " (or -Dllm.api.key.optional=true for mock gateways)");
        }
        // #5405: after this many consecutive heartbeat failures the process exits so a
        // supervisor can restart it; the runtime TTL has evicted the registration by then
        // and a silent zombie serves nothing.
        final int heartbeatFailLimit = Integer.getInteger("agent.heartbeat.failLimit", 6);

        // Step 1: register (gets the assigned agent-parent + client-reply-parent)
        AgentControlClient control = new AgentControlClient(runtimeUrl);
        AgentControlClient.RegisterResult reg = control.register(agentId, List.of(llmModel), capacity);
        String agentParent = reg.parent();
        log.info("agent registered: agentId={} parent={} clientParent={}", agentId, agentParent, reg.clientParent());

        // Step 2: subscribe the agent's channel
        CloudEventsClient client = CloudEventsClient.builder().runtimeUrl(runtimeUrl).clientId(agentId).build();
        OpenAiLlmClient llm = new OpenAiLlmClient(llmBase, llmKey, llmModel);
        ConversationStore store = new ConversationStore(maxHistory, maxConversations);
        StreamingAgent agent = new StreamingAgent(client, agentParent, agentId, llm, store);
        agent.start(); // subscribe (agentParent, agent.<agentId>)

        // Step 3: ready-before-route: only now is this agent eligible for matchmaking
        control.ready(agentId);
        log.info("agent READY: agentId={} (subscribed agent.{})", agentId, agentId);

        // Step 4: heartbeat loop (refresh TTL + report load). Consecutive failures beyond
        // agent.heartbeat.failLimit exit the process (nonzero) — the registration is gone by
        // then, so staying alive would only serve errors.
        final java.util.concurrent.atomic.AtomicInteger heartbeatFailures = new java.util.concurrent.atomic.AtomicInteger();
        Thread heartbeatThread = Thread.startVirtualThread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(heartbeatMs);
                    control.heartbeat(agentId, agent.activeSessions());
                    heartbeatFailures.set(0);
                } catch (InterruptedException ie) {
                    return;
                } catch (Exception e) {
                    int fails = heartbeatFailures.incrementAndGet();
                    log.warn("heartbeat failed ({}/{}): {}", fails, heartbeatFailLimit, e.toString());
                    if (fails >= heartbeatFailLimit) {
                        log.error("heartbeat failed {} consecutive times - registration is gone; exiting for supervisor restart",
                            fails);
                        Runtime.getRuntime().halt(1);
                    }
                }
            }
        });

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("shutting down agent...");
            heartbeatThread.interrupt();
            try {
                control.unregister(agentId);
            } catch (Exception e) {
                log.warn("unregister failed: {}", e.toString());
            }
            agent.shutdown();
            client.shutdown();
        }, "agent-shutdown"));

        log.info("AgentApplication running: runtime={} agentId={} parent={} model={} (Ctrl+C to stop)",
            runtimeUrl, agentId, agentParent, llmModel);
        Thread.currentThread().join();
    }
}
