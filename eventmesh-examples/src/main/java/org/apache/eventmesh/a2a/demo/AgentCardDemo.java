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

package org.apache.eventmesh.a2a.demo;

import org.apache.eventmesh.client.http.conf.EventMeshHttpClientConfig;
import org.apache.eventmesh.client.http.producer.EventMeshHttpProducer;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.ExampleConstants;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.protocol.a2a.A2AProtocolConstants;
import org.apache.eventmesh.protocol.a2a.model.AgentCapabilities;
import org.apache.eventmesh.protocol.a2a.model.AgentCard;
import org.apache.eventmesh.protocol.a2a.model.AgentInterface;
import org.apache.eventmesh.protocol.a2a.model.AgentProvider;
import org.apache.eventmesh.protocol.a2a.model.AgentSkill;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

import lombok.extern.slf4j.Slf4j;

/**
 * Demo showing A2A Agent Card registration, discovery, and deletion via EventMesh.
 */
@Slf4j
public class AgentCardDemo extends A2AAbstractDemo {

    public static void main(String[] args) throws Exception {
        EventMeshHttpClientConfig config = initEventMeshHttpClientConfig("a2a-agent-card-demo");
        try (EventMeshHttpProducer producer = new EventMeshHttpProducer(config)) {

            // 1. Register an Agent Card
            registerAgentCard(producer, "my.org", "my.unit", "weather-agent");

            // 2. List Agent Cards
            listAgentCards(producer);

            // 3. Get specific Agent Card
            getAgentCard(producer, "my.org", "my.unit", "weather-agent");

            // 4. Delete Agent Card
            deleteAgentCard(producer, "my.org", "my.unit", "weather-agent");

            log.info("AgentCardDemo completed.");
        }
    }

    private static void registerAgentCard(EventMeshHttpProducer producer,
                                           String orgId, String unitId, String agentId) throws Exception {
        AgentCard card = buildSampleCard(agentId);

        Map<String, Object> params = new HashMap<>();
        params.put("org_id", orgId);
        params.put("unit_id", unitId);
        params.put("agent_id", agentId);
        params.put("card", card);

        Map<String, Object> request = new HashMap<>();
        request.put("jsonrpc", "2.0");
        request.put("method", A2AProtocolConstants.OP_REGISTER_AGENT_CARD);
        request.put("params", params);
        request.put("id", UUID.randomUUID().toString());

        CloudEvent event = buildA2ACardEvent(request);
        producer.publish(event);
        log.info("Registered agent card: {}/{}/{}", orgId, unitId, agentId);
    }

    private static void listAgentCards(EventMeshHttpProducer producer) throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("org_id", "my.org");

        Map<String, Object> request = new HashMap<>();
        request.put("jsonrpc", "2.0");
        request.put("method", A2AProtocolConstants.OP_LIST_AGENT_CARDS);
        request.put("params", params);
        request.put("id", UUID.randomUUID().toString());

        CloudEvent event = buildA2ACardEvent(request);
        producer.publish(event);
        log.info("Listed agent cards for org: my.org");
    }

    private static void getAgentCard(EventMeshHttpProducer producer,
                                      String orgId, String unitId, String agentId) throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("org_id", orgId);
        params.put("unit_id", unitId);
        params.put("agent_id", agentId);

        Map<String, Object> request = new HashMap<>();
        request.put("jsonrpc", "2.0");
        request.put("method", A2AProtocolConstants.OP_GET_AGENT_CARD);
        request.put("params", params);
        request.put("id", UUID.randomUUID().toString());

        CloudEvent event = buildA2ACardEvent(request);
        producer.publish(event);
        log.info("Got agent card: {}/{}/{}", orgId, unitId, agentId);
    }

    private static void deleteAgentCard(EventMeshHttpProducer producer,
                                         String orgId, String unitId, String agentId) throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("org_id", orgId);
        params.put("unit_id", unitId);
        params.put("agent_id", agentId);

        Map<String, Object> request = new HashMap<>();
        request.put("jsonrpc", "2.0");
        request.put("method", A2AProtocolConstants.OP_DELETE_AGENT_CARD);
        request.put("params", params);
        request.put("id", UUID.randomUUID().toString());

        CloudEvent event = buildA2ACardEvent(request);
        producer.publish(event);
        log.info("Deleted agent card: {}/{}/{}", orgId, unitId, agentId);
    }

    private static AgentCard buildSampleCard(String agentId) {
        AgentInterface iface = AgentInterface.builder()
            .url("http://localhost:8080/a2a")
            .protocolBinding("JSONRPC")
            .protocolVersion(A2AProtocolConstants.PROTOCOL_VERSION)
            .build();

        AgentProvider provider = AgentProvider.builder()
            .url("https://example.org")
            .organization("Example Org")
            .build();

        AgentCapabilities capabilities = AgentCapabilities.builder()
            .streaming(true)
            .pushNotifications(true)
            .build();

        AgentSkill skill = AgentSkill.builder()
            .id("weather-query")
            .name("Weather Query")
            .description("Queries weather information for a given location")
            .tags(Arrays.asList("weather", "query"))
            .examples(Arrays.asList("What's the weather in Beijing?"))
            .build();

        return AgentCard.builder()
            .name(agentId)
            .description("A weather query agent")
            .version("1.0.0")
            .supportedInterfaces(Collections.singletonList(iface))
            .provider(provider)
            .capabilities(capabilities)
            .skills(Collections.singletonList(skill))
            .defaultInputModes(Collections.singletonList("text/plain"))
            .defaultOutputModes(Collections.singletonList("text/plain"))
            .build();
    }

    private static CloudEvent buildA2ACardEvent(Map<String, Object> jsonRpcBody) {
        String content = JsonUtils.toJSONString(jsonRpcBody);
        String method = (String) jsonRpcBody.get("method");
        String ceType = A2AProtocolConstants.CE_TYPE_PREFIX + method.replace("/", ".") + ".req";

        return CloudEventBuilder.v1()
            .withId(UUID.randomUUID().toString())
            .withSource(URI.create("a2a-agent-card-demo"))
            .withDataContentType(ExampleConstants.CLOUDEVENT_CONTENT_TYPE)
            .withType(ceType)
            .withData(content.getBytes(StandardCharsets.UTF_8))
            .withExtension(A2AProtocolConstants.CE_EXTENSION_PROTOCOL, "A2A")
            .withExtension(A2AProtocolConstants.CE_EXTENSION_PROTOCOL_VERSION, "2.0")
            .withExtension(A2AProtocolConstants.CE_EXTENSION_A2A_METHOD, method)
            .withExtension(A2AProtocolConstants.CE_EXTENSION_MCP_TYPE, "request")
            .withExtension(Constants.EVENTMESH_MESSAGE_CONST_TTL, String.valueOf(4_000))
            .build();
    }
}
