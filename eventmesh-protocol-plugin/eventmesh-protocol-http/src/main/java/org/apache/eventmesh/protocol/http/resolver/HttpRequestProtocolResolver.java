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

package org.apache.eventmesh.protocol.http.resolver;

import org.apache.eventmesh.common.protocol.http.HttpEventWrapper;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.protocol.api.exception.ProtocolHandleException;

import org.apache.commons.lang3.StringUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.v1.CloudEventBuilder;

import com.fasterxml.jackson.core.type.TypeReference;

public class HttpRequestProtocolResolver {

    public static CloudEvent buildEvent(HttpEventWrapper httpEventWrapper) throws ProtocolHandleException {

        try {
            CloudEventBuilder builder = new CloudEventBuilder();

            Map<String, Object> requestHeaderMap = httpEventWrapper.getHeaderMap();

            Map<String, Object> sysHeaderMap = httpEventWrapper.getSysHeaderMap();

            String id = sysHeaderMap.getOrDefault("id", UUID.randomUUID()).toString();

            String source = sysHeaderMap.getOrDefault("source", "/").toString();

            String type = sysHeaderMap.getOrDefault("type", "http_request").toString();

            String subject = sysHeaderMap.getOrDefault("subject", "TOPIC-HTTP-REQUEST").toString();

            // with attributes
            builder.withId(id)
                .withType(type)
                .withSource(URI.create("source:" + source))
                .withSubject(subject)
                .withDataContentType("application/json");

            // with extensions
            for (String extensionKey : sysHeaderMap.keySet()) {
                if (StringUtils.equals("id", extensionKey) || StringUtils.equals("source", extensionKey) || StringUtils.equals("type", extensionKey)
                    || StringUtils.equals("subject", extensionKey)) {
                    continue;
                }
                String lowerExtensionKey = extensionKey.toLowerCase();
                builder.withExtension(lowerExtensionKey, sysHeaderMap.get(extensionKey).toString());
            }

            byte[] requestBody = httpEventWrapper.getBody();

            Map<String, Object> requestBodyMap = JsonUtils.deserialize(new String(requestBody), new TypeReference<HashMap<String, Object>>() {
            });

            String requestURI = httpEventWrapper.getRequestURI();

            Map<String, Object> data = new HashMap<>();
            data.put("headers", requestHeaderMap);
            data.put("body", requestBodyMap);
            data.put("path", requestURI);
            data.put("method", httpEventWrapper.getHttpMethod());
            // with data
            builder = builder.withData(JsonUtils.serialize(data).getBytes(StandardCharsets.UTF_8));
            return builder.build();
        } catch (Exception e) {
            throw new ProtocolHandleException(e.getMessage(), e.getCause());
        }
    }
}
