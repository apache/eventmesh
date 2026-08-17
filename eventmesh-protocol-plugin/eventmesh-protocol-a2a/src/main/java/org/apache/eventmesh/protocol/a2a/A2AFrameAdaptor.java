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

package org.apache.eventmesh.protocol.a2a;

import org.apache.eventmesh.common.protocol.ByteTransport;
import org.apache.eventmesh.common.protocol.ProtocolTransportObject;
import org.apache.eventmesh.common.wire.EventMeshFrame;
import org.apache.eventmesh.protocol.api.FrameAdaptor;
import org.apache.eventmesh.protocol.api.exception.ProtocolHandleException;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * {@link FrameAdaptor} for the A2A (Agent-to-Agent) JSON-RPC 2.0 protocol. Converts A2A JSON-RPC
 * messages directly to/from {@link EventMeshFrame} — no CloudEvent intermediary.
 *
 * <p>Ingress: A2A JSON-RPC bytes → parse JSON-RPC → extract routing (method, params._topic,
 * agent-card identity) into frame attributes + raw JSON as data.
 * Egress: frame attributes + data → reconstruct A2A JSON-RPC bytes.</p>
 *
 * <p>Registered under the name {@code a2a}; load via
 * {@code FrameAdaptors.get("a2a")}.</p>
 */
public class A2AFrameAdaptor implements FrameAdaptor {

    public static final String PROTOCOL_TYPE = "a2a";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public EventMeshFrame toFrame(ProtocolTransportObject proto) throws ProtocolHandleException {
        byte[] jsonBytes;
        if (proto instanceof ByteTransport) {
            jsonBytes = ((ByteTransport) proto).getBytes();
        } else {
            jsonBytes = proto.toString().getBytes(StandardCharsets.UTF_8);
        }
        if (jsonBytes == null || jsonBytes.length == 0) {
            throw new ProtocolHandleException("empty A2A body");
        }
        try {
            String content = new String(jsonBytes, StandardCharsets.UTF_8);
            JsonNode node = MAPPER.readTree(content);

            Map<String, String> attrs = new LinkedHashMap<>();
            attrs.put("specversion", "1.0");
            attrs.put("type", "a2a");
            attrs.put("source", "eventmesh-a2a");
            attrs.put("id", node.has("id") ? node.get("id").asText() : java.util.UUID.randomUUID().toString());
            attrs.put(A2AProtocolConstants.CE_EXTENSION_PROTOCOL, PROTOCOL_TYPE);

            if (node.has("jsonrpc")) {
                attrs.put("ema2aversion", node.get("jsonrpc").asText());
            }
            if (node.has("method")) {
                String method = node.get("method").asText();
                attrs.put(A2AProtocolConstants.CE_EXTENSION_A2A_METHOD, method);
                attrs.put("type", A2AProtocolConstants.CE_TYPE_PREFIX + method.replace("/", ".") + ".req");
            } else if (node.has("result") || node.has("error")) {
                attrs.put("type", A2AProtocolConstants.CE_TYPE_PREFIX + "common.response");
            }

            // Extract routing from params
            if (node.has("params")) {
                JsonNode params = node.get("params");
                if (params.has("_topic")) {
                    attrs.put("subject", params.get("_topic").asText());
                } else if (params.has("org_id") && params.has("unit_id") && params.has("agent_id")) {
                    AgentIdentity identity = new AgentIdentity(
                        params.get("org_id").asText(),
                        params.get("unit_id").asText(),
                        params.get("agent_id").asText());
                    attrs.put("subject", identity.discoveryTopic());
                }
                if (params.has("_seq")) {
                    attrs.put(A2AProtocolConstants.CE_EXTENSION_SEQ, params.get("_seq").asText());
                }
                if (params.has("_agentId")) {
                    attrs.put(A2AProtocolConstants.CE_EXTENSION_TARGET_AGENT, params.get("_agentId").asText());
                }
            }

            return EventMeshFrame.event(attrs, jsonBytes);
        } catch (Exception e) {
            throw new ProtocolHandleException("A2A → EventMeshFrame conversion failed", e);
        }
    }

    @Override
    public ProtocolTransportObject fromFrame(EventMeshFrame frame) throws ProtocolHandleException {
        // A2A egress: the frame's data IS the original A2A JSON-RPC bytes (preserved verbatim on
        // ingress). Return as-is — the A2A client speaks JSON-RPC.
        return new ByteTransport(frame.data());
    }

    @Override
    public String getProtocolType() {
        return PROTOCOL_TYPE;
    }
}
