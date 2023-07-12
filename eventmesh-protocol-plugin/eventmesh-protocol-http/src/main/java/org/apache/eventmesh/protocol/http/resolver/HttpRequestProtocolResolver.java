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
import org.apache.eventmesh.protocol.http.HttpProtocolConstant;

import org.apache.commons.lang3.StringUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
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

            String id = sysHeaderMap.getOrDefault(HttpProtocolConstant.CONSTANTS_KEY_ID, UUID.randomUUID()).toString();

            String source = sysHeaderMap.getOrDefault(HttpProtocolConstant.CONSTANTS_KEY_SOURCE,
                HttpProtocolConstant.CONSTANTS_DEFAULT_SOURCE).toString();

            String type = sysHeaderMap.getOrDefault(HttpProtocolConstant.CONSTANTS_KEY_TYPE,
                HttpProtocolConstant.CONSTANTS_DEFAULT_TYPE).toString();

            String subject = sysHeaderMap.getOrDefault(HttpProtocolConstant.CONSTANTS_KEY_SUBJECT,
                HttpProtocolConstant.CONSTANTS_DEFAULT_SUBJECT).toString();

            String dataContentType = requestHeaderMap.getOrDefault(HttpProtocolConstant.DATA_CONTENT_TYPE,
                HttpProtocolConstant.APPLICATION_JSON).toString();
            // with attributes
            builder.withId(id)
                .withType(type)
                .withSource(URI.create(HttpProtocolConstant.CONSTANTS_KEY_SOURCE + ":" + source))
                .withSubject(subject)
                .withDataContentType(dataContentType);

            // with extensions
            for (Map.Entry<String, Object> extension : sysHeaderMap.entrySet()) {
                if (StringUtils.equals(HttpProtocolConstant.CONSTANTS_KEY_ID, extension.getKey())
                    || StringUtils.equals(HttpProtocolConstant.CONSTANTS_KEY_SOURCE, extension.getKey())
                    || StringUtils.equals(HttpProtocolConstant.CONSTANTS_KEY_TYPE, extension.getKey())
                    || StringUtils.equals(HttpProtocolConstant.CONSTANTS_KEY_SUBJECT, extension.getKey())) {
                    continue;
                }
                String lowerExtensionKey = extension.getKey().toLowerCase(Locale.getDefault());
                builder.withExtension(lowerExtensionKey, sysHeaderMap.get(extension.getKey()).toString());
            }

            byte[] requestBody = httpEventWrapper.getBody();

            if (StringUtils.equals(dataContentType, HttpProtocolConstant.APPLICATION_JSON)) {
                Map<String, Object> requestBodyMap = JsonUtils.parseTypeReferenceObject(new String(requestBody),
                    new TypeReference<HashMap<String, Object>>() {});

                String requestURI = httpEventWrapper.getRequestURI();

                Map<String, Object> data = new HashMap<>();
                data.put(HttpProtocolConstant.CONSTANTS_KEY_HEADERS, requestHeaderMap);
                data.put(HttpProtocolConstant.CONSTANTS_KEY_BODY, requestBodyMap);
                data.put(HttpProtocolConstant.CONSTANTS_KEY_PATH, requestURI);
                data.put(HttpProtocolConstant.CONSTANTS_KEY_METHOD, httpEventWrapper.getHttpMethod());
                // with data
                builder = builder.withData(JsonUtils.toJSONString(data).getBytes(StandardCharsets.UTF_8));
            } else if (StringUtils.equals(dataContentType, HttpProtocolConstant.PROTOBUF)) {
                // with data
                builder = builder.withData(requestBody);
            }
            return builder.build();
        } catch (Exception e) {
            throw new ProtocolHandleException(e.getMessage(), e);
        }
    }
}
