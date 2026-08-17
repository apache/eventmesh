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
        String runtimeUrl = System.getProperty("agent.runtime.url", "http://localhost:8080");
        String agentId = System.getProperty("agent.id", "agent-" + Long.toString(System.currentTimeMillis(), 36));
        String llmBase = System.getProperty("llm.base.url", "https://api.openai.com");
        String llmKey = System.getProperty("llm.api.key", "");
        String llmModel = System.getProperty("llm.model", "gpt-4o-mini");
        final long heartbeatMs = Long.getLong("agent.heartbeat.intervalMs", 10_000L);
        int maxHistory = Integer.getInteger("agent.conversation.maxHistory", 20);
        int capacity = Integer.getInteger("agent.capacity", 100);

        // Step 1: register (gets the assigned agent-parent + client-reply-parent)
        AgentControlClient control = new AgentControlClient(runtimeUrl);
        AgentControlClient.RegisterResult reg = control.register(agentId, List.of(llmModel), capacity);
        String agentParent = reg.parent();
        log.info("agent registered: agentId={} parent={} clientParent={}", agentId, agentParent, reg.clientParent());

        // Step 2: subscribe the agent's channel
        CloudEventsClient client = CloudEventsClient.builder().runtimeUrl(runtimeUrl).clientId(agentId).build();
        OpenAiLlmClient llm = new OpenAiLlmClient(llmBase, llmKey, llmModel);
        ConversationStore store = new ConversationStore(maxHistory);
        StreamingAgent agent = new StreamingAgent(client, agentParent, agentId, llm, store);
        agent.start(); // subscribe (agentParent, agent.<agentId>)

        // Step 3: ready-before-route: only now is this agent eligible for matchmaking
        control.ready(agentId);
        log.info("agent READY: agentId={} (subscribed agent.{})", agentId, agentId);

        // Step 4: heartbeat loop (refresh TTL + report load)
        Thread heartbeatThread = Thread.startVirtualThread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(heartbeatMs);
                    control.heartbeat(agentId, agent.activeSessions());
                } catch (InterruptedException ie) {
                    return;
                } catch (Exception e) {
                    log.warn("heartbeat failed: {}", e.toString());
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
