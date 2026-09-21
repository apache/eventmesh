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
import org.apache.eventmesh.agent.tool.AgentTool;
import org.apache.eventmesh.agent.tool.ConnectorToolAdapter;
import org.apache.eventmesh.agent.tool.ToolRegistry;
import org.apache.eventmesh.client.cloudevents.CloudEventsClient;
import org.apache.eventmesh.connector.SinkConnector;
import org.apache.eventmesh.connector.SourceConnector;

import java.util.List;
import java.util.Properties;

import lombok.extern.slf4j.Slf4j;

/**
 * Agent process entry (v2). Boot sequence (§5.2): register with the runtime (get its agent-parent) →
 * subscribe its {@code agent.<agentId>} channel - flip READY (ready-before-route) - heartbeat loop.
 * All config via {@code -D} system properties.
 *
 * <p>Config keys: {@code agent.runtime.url}, {@code agent.id}, {@code agent.heartbeat.intervalMs},
 * {@code agent.capacity}, {@code agent.conversation.maxHistory}, {@code llm.base.url},
 * {@code llm.api.key}, {@code llm.model}; connector tools via
 * {@code agent.tools.sink.<name>=<fqcn>} / {@code agent.tools.source.<name>=<fqcn>}
 * (+ {@code agent.tools.props.<name>.*}); event triggers via {@code agent.subscribe.topics} +
 * {@code agent.trigger.output.topic}.</p>
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
        String triggerTopics = System.getProperty("agent.subscribe.topics", "");
        String triggerOutput = System.getProperty("agent.trigger.output.topic", "agent.triggers");
        String spiTools = System.getProperty("agent.tools.spi", "");

        // Step 1: register (gets the assigned agent-parent + client-reply-parent)
        AgentControlClient control = new AgentControlClient(runtimeUrl);
        AgentControlClient.RegisterResult reg = control.register(agentId, List.of(llmModel), capacity);
        String agentParent = reg.parent();
        log.info("agent registered: agentId={} parent={} clientParent={}", agentId, agentParent, reg.clientParent());

        // Step 2: subscribe the agent's channel (with connector tools, if configured)
        CloudEventsClient client = CloudEventsClient.builder().runtimeUrl(runtimeUrl).clientId(agentId).build();
        OpenAiLlmClient llm = new OpenAiLlmClient(llmBase, llmKey, llmModel);
        ConversationStore store = new ConversationStore(maxHistory, maxConversations);
        ToolRegistry tools = buildConnectorTools();
        StreamingAgent agent = new StreamingAgent(client, agentParent, agentId, llm, store, tools);
        agent.start(); // subscribe (agentParent, agent.<agentId>)

        // Step 2b: optional event-driven triggers (own client so the lite-channel poller is untouched)
        CloudEventsClient triggerClient = null;
        if (!triggerTopics.isBlank()) {
            triggerClient = CloudEventsClient.builder().runtimeUrl(runtimeUrl)
                .clientId(agentId + "-triggers").build();
            for (String topic : triggerTopics.split(",")) {
                String trimmed = topic.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                triggerClient.subscribe(trimmed, "LOAD_BALANCE", event -> agent.onEvent(event, triggerOutput));
            }
            log.info("agent trigger subscriptions: [{}] -> output topic {}", triggerTopics, triggerOutput);
        }

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

        final CloudEventsClient finalTriggerClient = triggerClient;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("shutting down agent...");
            heartbeatThread.interrupt();
            try {
                control.unregister(agentId);
            } catch (Exception e) {
                log.warn("unregister failed: {}", e.toString());
            }
            agent.shutdown();
            if (finalTriggerClient != null) {
                finalTriggerClient.shutdown();
            }
            client.shutdown();
        }, "agent-shutdown"));

        log.info("AgentApplication running: runtime={} agentId={} parent={} model={} tools={} (Ctrl+C to stop)",
            runtimeUrl, agentId, agentParent, llmModel, tools.size());
        Thread.currentThread().join();
    }

    /**
     * Build the connector-backed tool registry from {@code -Dagent.tools.sink.<name>=<fqcn>} /
     * {@code -Dagent.tools.source.<name>=<fqcn>} plus per-tool connector properties under
     * {@code agent.tools.props.<name>.*}. Empty registry when nothing is configured.
     */
    private static ToolRegistry buildConnectorTools() {
        ToolRegistry registry = new ToolRegistry();
        for (String key : System.getProperties().stringPropertyNames()) {
            if (key.startsWith("agent.tools.sink.")) {
                String name = key.substring("agent.tools.sink.".length());
                registerConnectorTool(registry, "sink", name, System.getProperty(key));
            } else if (key.startsWith("agent.tools.source.")) {
                String name = key.substring("agent.tools.source.".length());
                registerConnectorTool(registry, "source", name, System.getProperty(key));
            }
        }
        // SPI-deployed custom tools (jars in plugin/agent/ with a META-INF/eventmesh service file)
        String spiTools = System.getProperty("agent.tools.spi", "");
        for (String spiName : spiTools.split(",")) {
            String trimmed = spiName.trim();
            if (!trimmed.isEmpty()) {
                registry.registerSpi(trimmed);
                log.info("SPI agent tool registered: {}", trimmed);
            }
        }
        return registry;
    }

    private static void registerConnectorTool(ToolRegistry registry, String kind, String name, String fqcn) {
        Properties props = new Properties();
        String prefix = "agent.tools.props." + name + ".";
        for (String key : System.getProperties().stringPropertyNames()) {
            if (key.startsWith(prefix)) {
                props.put(key.substring(prefix.length()), System.getProperty(key));
            }
        }
        AgentTool tool;
        if ("sink".equals(kind)) {
            tool = ConnectorToolAdapter.sinkTool(name, "Deliver a JSON payload through the '" + name + "' connector",
                loadConnector(fqcn, SinkConnector.class), props);
        } else {
            tool = ConnectorToolAdapter.sourceTool(name, "Poll the next event batch from the '" + name
                + "' connector", loadConnector(fqcn, SourceConnector.class), props);
        }
        registry.register(tool);
        log.info("connector tool registered: {}={} ({})", kind, name, fqcn);
    }

    private static <T> Class<? extends T> loadConnector(String fqcn, Class<T> spi) {
        try {
            return Class.forName(fqcn).asSubclass(spi);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("connector class not found on agent classpath: " + fqcn, e);
        }
    }
}
