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

package org.apache.eventmesh.runtime.it;

import org.apache.eventmesh.agent.AgentControlClient;
import org.apache.eventmesh.agent.ConversationStore;
import org.apache.eventmesh.agent.StreamingAgent;
import org.apache.eventmesh.agent.llm.OpenAiLlmClient;
import org.apache.eventmesh.client.cloudevents.CloudEventsClient;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Standalone streaming Agent launcher — registers one agent against a running EventMesh Runtime
 * (started via {@link RuntimeLauncher}) and connects it to the real LLM gateway. Run one instance
 * per terminal, each with a different {@code agentId} (first arg).
 *
 * <p>Usage:<pre>
 *   gradle :eventmesh-runtime:startAgent --args="agent1"
 *   gradle :eventmesh-runtime:startAgent --args="agent2"
 *   # LLM API key: -Dllm.api.key=sk-...  (or set LLM_API_KEY env var)
 *   # override: -Dem.runtimeUrl=http://host:port -Dem.agentParent=em-agent -Dllm.model=gpt-4
 * </pre>
 */
public class AgentLauncher {

    private static final Logger log = LoggerFactory.getLogger(AgentLauncher.class);

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            log.warn("Usage: AgentLauncher <agentId>");
            System.exit(1);
        }
        String agentId = args[0];
        String runtimeUrl = System.getProperty("em.runtimeUrl", "http://localhost:8080");
        String agentParent = System.getProperty("em.agentParent", "em-agent");
        String llmBaseUrl = System.getProperty("llm.base.url", E2EConfig.LLM_BASE_URL);
        String llmApiKey = System.getProperty("llm.api.key",
            System.getenv().getOrDefault("LLM_API_KEY", ""));
        String llmModel = System.getProperty("llm.model", E2EConfig.LLM_MODEL);

        if (llmApiKey.isEmpty()) {
            log.warn("LLM_API_KEY not set. Pass -Dllm.api.key=sk-... or set LLM_API_KEY env var.");
            System.exit(1);
        }

        log.info("=== AgentLauncher: agentId={} runtime={} agentParent={} ===", agentId, runtimeUrl, agentParent);
        log.info("  LLM: baseUrl={} model={}", llmBaseUrl, llmModel);

        CloudEventsClient agentClient = CloudEventsClient.builder()
            .runtimeUrl(runtimeUrl).clientId(agentId).build();
        OpenAiLlmClient llm = new OpenAiLlmClient(llmBaseUrl, llmApiKey, llmModel);
        ConversationStore conversations = new ConversationStore(20);
        StreamingAgent agent = new StreamingAgent(agentClient, agentParent, agentId, llm, conversations);
        agent.start();

        AgentControlClient control = new AgentControlClient(runtimeUrl);
        control.register(agentId, List.of(llmModel), 100);
        control.ready(agentId);
        log.info("agent registered + ready: agentId={} model={}", agentId, llmModel);

        // Heartbeat keeps the agent fresh (30s TTL) so the matchmaker picks it.
        Thread heartbeat = Thread.startVirtualThread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(5_000L);
                    control.heartbeat(agentId, agent.activeSessions());
                } catch (InterruptedException e) {
                    return;
                } catch (Exception ignore) {
                    // best-effort
                }
            }
        });

        // Block until killed (Ctrl+C).
        Thread main = Thread.currentThread();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("shutting down agent {}...", agentId);
            heartbeat.interrupt();
            try {
                control.unregister(agentId);
            } catch (Exception ignore) {
                // best-effort unregister during shutdown
            }
            agent.shutdown();
            agentClient.shutdown();
            main.interrupt();
        }));
        Thread.currentThread().join();
    }
}