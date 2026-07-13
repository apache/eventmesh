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

package org.apache.eventmesh.a2a.demo.gateway;

import org.apache.eventmesh.protocol.a2a.A2AClient;
import org.apache.eventmesh.protocol.a2a.model.AgentCard;
import org.apache.eventmesh.protocol.a2a.model.AgentSkill;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

/**
 * A2A Gateway Client Demo.
 *
 * <p>This is a pure HTTP client demo that connects to a running A2A Gateway Server.
 * It does NOT depend on any runtime server-side classes.
 *
 * <p>Prerequisites:
 * <ol>
 *   <li>Start the A2A Gateway Server first:
 *       <pre>java org.apache.eventmesh.runtime.a2a.A2AGatewayServer</pre>
 *       (server listens on port 10105 by default, with a pre-registered weather-agent)</li>
 *   <li>Then run this client demo.</li>
 * </ol>
 *
 * <p>Flow:
 * <pre>
 *   Client -HTTP-&gt; Gateway Server -InMemory-&gt; Weather Agent -&gt; Gateway -&gt; Client
 * </pre>
 */
@Slf4j
public class A2AGatewayDemo {

    private static final String GATEWAY_URL = "http://localhost:10105";
    private static final String NAMESPACE = "global";

    public static void main(String[] args) throws Exception {
        log.info("=== A2A Gateway Client Demo ===");
        log.info("Connecting to Gateway: {}", GATEWAY_URL);

        // 1. Create A2A client (as a caller, not an agent)
        A2AClient client = A2AClient.builder()
            .gatewayUrl(GATEWAY_URL)
            .namespace(NAMESPACE)
            .agentName("demo-client")
            .agentCard(AgentCard.builder()
                .name("demo-client")
                .description("A demo client that calls weather-agent")
                .version("1.0.0")
                .supportedInterfaces(Arrays.asList(
                    org.apache.eventmesh.protocol.a2a.model.AgentInterface.builder()
                        .url("http://localhost:0/a2a")
                        .protocolBinding("JSONRPC")
                        .protocolVersion("0.3")
                        .build()
                ))
                .capabilities(org.apache.eventmesh.protocol.a2a.model.AgentCapabilities.builder()
                    .streaming(false)
                    .pushNotifications(false)
                    .build())
                .skills(Arrays.asList(
                    AgentSkill.builder()
                        .id("call-weather")
                        .name("Call Weather")
                        .description("Call the weather agent for weather info")
                        .tags(Arrays.asList("demo", "client"))
                        .build()
                ))
                .defaultInputModes(Arrays.asList("text/plain"))
                .defaultOutputModes(Arrays.asList("text/plain"))
                .build())
            .heartbeatInterval(60_000)
            .build();

        client.start();
        log.info("Client started.");

        // 2. List registered agents
        log.info("");
        log.info("--- Listing registered agents ---");
        List<String> agents = client.listAgents();
        log.info("Agents: {}", agents);

        // 3. Submit a task to weather-agent (sync mode)
        log.info("");
        log.info("--- Submitting task to weather-agent ---");
        A2AClient.TaskResult result = client.sendTaskSync("weather-agent", "Beijing", null);
        log.info("Result: state={}, data={}", result.getState(), result.getData());

        // 4. Submit another task
        log.info("");
        log.info("--- Submitting second task ---");
        A2AClient.TaskResult result2 = client.sendTaskSync("weather-agent", "Shanghai", null);
        log.info("Result: state={}, data={}", result2.getState(), result2.getData());

        // 5. Async task submission
        log.info("");
        log.info("--- Async task submission ---");
        java.util.concurrent.CompletableFuture<A2AClient.TaskResult> future =
            client.sendTask("weather-agent", "Guangzhou");
        A2AClient.TaskResult result3 = future.get(30, TimeUnit.SECONDS);
        log.info("Async result: state={}, data={}", result3.getState(), result3.getData());

        // 6. Query task status
        log.info("");
        log.info("--- Querying task status ---");
        A2AClient.TaskResult status = client.getTaskStatus(result.getTaskId());
        log.info("Status: state={}, targetAgent={}", status.getState(), status.getTargetAgent());

        // 6. Cleanup
        log.info("");
        log.info("=== Demo Complete ===");
        client.shutdown();
    }
}
