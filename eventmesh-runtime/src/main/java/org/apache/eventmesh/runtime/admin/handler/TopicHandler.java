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

package org.apache.eventmesh.runtime.admin.handler;

import org.apache.eventmesh.api.admin.TopicProperties;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.runtime.admin.request.CreateTopicRequest;
import org.apache.eventmesh.runtime.admin.request.DeleteTopicRequest;
import org.apache.eventmesh.runtime.common.EventHttpHandler;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.plugin.MQAdminWrapper;
import org.apache.eventmesh.runtime.util.HttpResponseUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;

import lombok.extern.slf4j.Slf4j;

/**
 * This class handles the {@code /topic} endpoint, corresponding to the {@code eventmesh-dashboard} path {@code /topic}, including the "Create Topic"
 * and "Remove" buttons.
 * <p>
 * It provides functionality for managing topics, including retrieving the list of topics (GET), creating a new topic (POST), and deleting an existing
 * topic (DELETE).
 * <p>
 * An instance of {@link MQAdminWrapper} is used to interact with the messaging system.
 *
 * @see AbstractHttpHandler
 * @see MQAdminWrapper
 */

@Slf4j
@EventHttpHandler(path = "/topic")
public class TopicHandler extends AbstractHttpHandler {

    private final MQAdminWrapper admin;

    /**
     * Constructs a new instance with the specified connector plugin type.
     *
     * @param connectorPluginType The name of event storage connector plugin.
     */
    public TopicHandler(
        String connectorPluginType) {
        super();
        admin = new MQAdminWrapper(connectorPluginType);
        try {
            admin.init(null);
        } catch (Exception ignored) {
            log.info("failed to initialize MQAdminWrapper");
        }
    }

    @Override
    protected void get(HttpRequest httpRequest, ChannelHandlerContext ctx) throws Exception {
        HttpHeaders responseHeaders = new DefaultHttpHeaders();
        responseHeaders.add(EventMeshConstants.CONTENT_TYPE, EventMeshConstants.APPLICATION_JSON);
        responseHeaders.add(EventMeshConstants.HANDLER_ORIGIN, "*");
        List<TopicProperties> topicList = admin.getTopic();
        String result = JsonUtils.toJSONString(topicList);
        HttpResponse httpResponse =
            HttpResponseUtils.getHttpResponse(Objects.requireNonNull(result).getBytes(Constants.DEFAULT_CHARSET), ctx, responseHeaders,
                HttpResponseStatus.OK);
        write(ctx, httpResponse);

    }

    @Override
    protected void post(HttpRequest httpRequest, ChannelHandlerContext ctx) throws Exception {
        Map<String, Object> body = parseHttpRequestBody(httpRequest);
        Objects.requireNonNull(body, "body can not be null");
        HttpHeaders responseHeaders = new DefaultHttpHeaders();
        responseHeaders.add(EventMeshConstants.CONTENT_TYPE, EventMeshConstants.APPLICATION_JSON);
        responseHeaders.add(EventMeshConstants.HANDLER_ORIGIN, "*");
        CreateTopicRequest createTopicRequest = JsonUtils.mapToObject(body, CreateTopicRequest.class);
        String topicName = Objects.requireNonNull(createTopicRequest).getName();
        admin.createTopic(topicName);
        writeSuccess(ctx);

    }

    @Override
    protected void delete(HttpRequest httpRequest, ChannelHandlerContext ctx) throws Exception {
        Map<String, Object> body = parseHttpRequestBody(httpRequest);
        Objects.requireNonNull(body, "body can not be null");
        HttpHeaders responseHeaders = new DefaultHttpHeaders();
        responseHeaders.add(EventMeshConstants.CONTENT_TYPE, EventMeshConstants.APPLICATION_JSON);
        responseHeaders.add(EventMeshConstants.HANDLER_ORIGIN, "*");
        DeleteTopicRequest deleteTopicRequest = JsonUtils.mapToObject(body, DeleteTopicRequest.class);
        String topicName = Objects.requireNonNull(deleteTopicRequest).getName();
        admin.deleteTopic(topicName);
        writeSuccess(ctx);

    }

}
