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

package org.apache.eventmesh.runtime.admin.handler.v1;

import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.runtime.admin.handler.AbstractHttpHandler;
import org.apache.eventmesh.runtime.common.EventMeshHttpHandler;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.plugin.MQAdminWrapper;
import org.apache.eventmesh.runtime.util.HttpRequestUtil;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.provider.EventFormatProvider;
import io.cloudevents.jackson.JsonFormat;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;

import lombok.extern.slf4j.Slf4j;

/**
 * This class handles the {@code /event} endpoint, corresponding to the {@code eventmesh-dashboard} path {@code /event}.
 * <p>
 * It is responsible for managing operations on events, including retrieving the event list and creating events.
 * <p>
 * The GET method supports querying events by {@code topicName}, and uses {@code offset} and {@code length} parameters for pagination.
 * <p>
 * An instance of {@link MQAdminWrapper} is used to interact with the messaging system.
 *
 * @see AbstractHttpHandler
 * @see MQAdminWrapper
 */

@Slf4j
@EventMeshHttpHandler(path = "/event")
public class EventHandler extends AbstractHttpHandler {

    private final MQAdminWrapper admin;

    /**
     * Constructs a new instance with the specified connector plugin type.
     *
     * @param connectorPluginType The name of event storage connector plugin.
     */
    public EventHandler(
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
        responseHeaders.add(EventMeshConstants.HANDLER_ORIGIN, "*");
        String queryString = URI.create(httpRequest.uri()).getQuery();
        if (queryString == null || queryString.isEmpty()) {
            writeUnauthorized(ctx, "");
            return;
        }
        Map<String, String> queryMap = HttpRequestUtil.queryStringToMap(queryString);
        String topicName = queryMap.get("topicName");
        int offset = Integer.parseInt(queryMap.get("offset"));
        int length = Integer.parseInt(queryMap.get("length"));
        List<CloudEvent> eventList = admin.getEvent(topicName, offset, length);

        List<String> eventJsonList = new ArrayList<>();
        for (CloudEvent event : eventList) {
            byte[] serializedEvent = Objects.requireNonNull(EventFormatProvider
                    .getInstance()
                    .resolveFormat(JsonFormat.CONTENT_TYPE))
                .serialize(event);
            eventJsonList.add(new String(serializedEvent, StandardCharsets.UTF_8));
        }
        String result = JsonUtils.toJSONString(eventJsonList);
        writeJson(ctx, result);
    }

    @Override
    protected void post(HttpRequest httpRequest, ChannelHandlerContext ctx) throws Exception {
        HttpHeaders responseHeaders = new DefaultHttpHeaders();
        responseHeaders.add(EventMeshConstants.HANDLER_ORIGIN, "*");
        String request = JsonUtils.toJSONString(HttpRequestUtil.parseHttpRequestBody(httpRequest));
        byte[] rawRequest = request.getBytes(StandardCharsets.UTF_8);
        CloudEvent event = Objects.requireNonNull(EventFormatProvider
            .getInstance()
            .resolveFormat(JsonFormat.CONTENT_TYPE)).deserialize(rawRequest);
        admin.publish(event);
        writeText(ctx, "");
    }
}
