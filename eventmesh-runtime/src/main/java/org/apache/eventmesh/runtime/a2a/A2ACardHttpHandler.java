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

import org.apache.eventmesh.protocol.a2a.AgentIdentity;
import org.apache.eventmesh.protocol.a2a.model.AgentCard;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * HTTP handler for A2A Agent Card Registry API.
 * Matches EMQX's API pattern:
 * - GET  /a2a/cards/list                           - list agent cards
 * - GET  /a2a/cards/card/{org_id}/{unit_id}/{agent_id}  - get specific card
 * - POST /a2a/cards/card/{org_id}/{unit_id}/{agent_id}  - register card
 * - DELETE /a2a/cards/card/{org_id}/{unit_id}/{agent_id} - delete card
 */
@Slf4j
public class A2ACardHttpHandler {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final String PATH_LIST = "/a2a/cards/list";
    private static final String PATH_CARD_PREFIX = "/a2a/cards/card/";

    private final A2APublishSubscribeService a2aService;

    public A2ACardHttpHandler(A2APublishSubscribeService a2aService) {
        this.a2aService = a2aService;
    }

    public FullHttpResponse handle(HttpRequest httpRequest, ChannelHandlerContext ctx) throws Exception {
        String uri = httpRequest.uri();
        QueryStringDecoder decoder = new QueryStringDecoder(uri);
        String path = decoder.path();
        HttpMethod method = httpRequest.method();

        try {
            // Handle CORS preflight
            if (method == HttpMethod.OPTIONS) {
                return corsResponse();
            }

            if (path.equals(PATH_LIST)) {
                return handleList(decoder);
            }

            if (path.startsWith(PATH_CARD_PREFIX)) {
                String[] segments = path.substring(PATH_CARD_PREFIX.length()).split("/");
                if (segments.length != 3) {
                    return jsonResponse(HttpResponseStatus.BAD_REQUEST,
                        errorBody("Invalid path. Expected /a2a/cards/card/{org_id}/{unit_id}/{agent_id}"));
                }

                String orgId = segments[0];
                String unitId = segments[1];
                String agentId = segments[2];

                if (method == HttpMethod.GET) {
                    return handleGet(orgId, unitId, agentId);
                } else if (method == HttpMethod.POST) {
                    return handleRegister(orgId, unitId, agentId, httpRequest);
                } else if (method == HttpMethod.DELETE) {
                    return handleDelete(orgId, unitId, agentId);
                }
            }

            return jsonResponse(HttpResponseStatus.NOT_FOUND, errorBody("Not found: " + path));
        } catch (Exception e) {
            log.error("A2A card handler error: {}", e.getMessage(), e);
            return jsonResponse(HttpResponseStatus.INTERNAL_SERVER_ERROR, errorBody(e.getMessage()));
        }
    }

    private FullHttpResponse handleList(QueryStringDecoder decoder) throws Exception {
        String orgId = getQueryParam(decoder, "org_id");
        String unitId = getQueryParam(decoder, "unit_id");
        String agentId = getQueryParam(decoder, "agent_id");

        List<A2APublishSubscribeService.CardEntry> cards = a2aService.listCards(orgId, unitId, agentId);
        String body = objectMapper.writeValueAsString(cards);
        return jsonResponse(HttpResponseStatus.OK, body);
    }

    private FullHttpResponse handleGet(String orgId, String unitId, String agentId) throws Exception {
        AgentIdentity identity = AgentIdentity.builder()
            .orgId(orgId).unitId(unitId).agentId(agentId).build();
        A2APublishSubscribeService.CardEntry card = a2aService.getCard(identity);

        if (card == null) {
            return jsonResponse(HttpResponseStatus.NOT_FOUND, errorBody("Card not found"));
        }

        String body = objectMapper.writeValueAsString(formatCardOut(card));
        return jsonResponse(HttpResponseStatus.OK, body);
    }

    private FullHttpResponse handleRegister(String orgId, String unitId, String agentId, HttpRequest request) throws Exception {
        // Read body from request
        String requestBody = "";
        if (request instanceof io.netty.handler.codec.http.FullHttpRequest) {
            ByteBuf content = ((io.netty.handler.codec.http.FullHttpRequest) request).content();
            requestBody = content.toString(StandardCharsets.UTF_8);
        }

        AgentCard card;
        try {
            card = objectMapper.readValue(requestBody, AgentCard.class);
        } catch (Exception e) {
            return jsonResponse(HttpResponseStatus.BAD_REQUEST, errorBody("Invalid card JSON: " + e.getMessage()));
        }

        AgentIdentity identity = AgentIdentity.builder()
            .orgId(orgId).unitId(unitId).agentId(agentId).build();
        A2APublishSubscribeService.RegistrationResult result = a2aService.registerCard(identity, card);

        if (result.isSuccess()) {
            return jsonResponse(HttpResponseStatus.NO_CONTENT, "");
        } else {
            return jsonResponse(HttpResponseStatus.BAD_REQUEST, errorBody(result.getErrorMessage()));
        }
    }

    private FullHttpResponse handleDelete(String orgId, String unitId, String agentId) throws Exception {
        AgentIdentity identity = AgentIdentity.builder()
            .orgId(orgId).unitId(unitId).agentId(agentId).build();
        boolean deleted = a2aService.deleteCard(identity);

        if (deleted) {
            return jsonResponse(HttpResponseStatus.NO_CONTENT, "");
        } else {
            return jsonResponse(HttpResponseStatus.NOT_FOUND, errorBody("Card not found"));
        }
    }

    private Map<String, Object> formatCardOut(A2APublishSubscribeService.CardEntry entry) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("namespace", entry.getNamespace());
        out.put("id", entry.getId());
        out.put("name", entry.getName());
        out.put("version", entry.getVersion());
        out.put("description", entry.getDescription());
        out.put("status", entry.getStatus());
        return out;
    }

    private String getQueryParam(QueryStringDecoder decoder, String key) {
        List<String> values = decoder.parameters().get(key);
        return (values != null && !values.isEmpty()) ? values.get(0) : null;
    }

    private String errorBody(String message) {
        try {
            return objectMapper.writeValueAsString(Collections.singletonMap("error", message));
        } catch (Exception e) {
            return "{\"error\":\"" + message + "\"}";
        }
    }

    private FullHttpResponse jsonResponse(HttpResponseStatus status, String body) {
        ByteBuf content = Unpooled.copiedBuffer(body, StandardCharsets.UTF_8);
        DefaultFullHttpResponse response = new DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1, status, content);
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=utf-8");
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes());
        setCorsHeaders(response);
        return response;
    }

    private FullHttpResponse corsResponse() {
        DefaultFullHttpResponse response = new DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        setCorsHeaders(response);
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, 0);
        return response;
    }

    private void setCorsHeaders(FullHttpResponse response) {
        response.headers().set("Access-Control-Allow-Origin", "*");
        response.headers().set("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
        response.headers().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
        response.headers().set("Access-Control-Max-Age", "3600");
    }
}
