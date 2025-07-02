/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     Mcp://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.eventmesh.protocol.mcp.resolver;

import com.fasterxml.jackson.core.type.TypeReference;
import io.cloudevents.CloudEvent;
import io.cloudevents.core.v1.CloudEventBuilder;
import org.apache.commons.lang3.StringUtils;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.protocol.api.exception.ProtocolHandleException;
import org.apache.eventmesh.protocol.mcp.McpProtocolConstant;
import org.apache.eventmesh.common.protocol.mcp.McpEventWrapper;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class McpRequestProtocolResolver {

    public static CloudEvent buildEvent(McpEventWrapper mcpEventWrapper) throws ProtocolHandleException {
        try {
            CloudEventBuilder builder = new CloudEventBuilder();

            Map<String, Object> requestHeaderMap = mcpEventWrapper.getHeaderMap();
            Map<String, Object> sysHeaderMap = mcpEventWrapper.getSysHeaderMap();

            String id = sysHeaderMap.getOrDefault(McpProtocolConstant.CONSTANTS_KEY_ID, UUID.randomUUID()).toString();
            String source = sysHeaderMap.getOrDefault(McpProtocolConstant.CONSTANTS_KEY_SOURCE,
                    McpProtocolConstant.CONSTANTS_DEFAULT_SOURCE).toString();
            String type = sysHeaderMap.getOrDefault(McpProtocolConstant.CONSTANTS_KEY_TYPE,
                    McpProtocolConstant.CONSTANTS_DEFAULT_TYPE).toString();
            String subject = sysHeaderMap.getOrDefault(McpProtocolConstant.CONSTANTS_KEY_SUBJECT,
                    McpProtocolConstant.CONSTANTS_DEFAULT_SUBJECT).toString();

            String dataContentType = requestHeaderMap.getOrDefault(McpProtocolConstant.CONSTANTS_DATA_CONTENT_TYPE,
                    McpProtocolConstant.CONSTANTS_APPLICATION_JSON).toString();

            // Set basic CloudEvent attributes
            builder.withId(id)
                    .withType(type)
                    .withSource(URI.create(source))
                    .withSubject(subject)
                    .withDataContentType(dataContentType);

            // Set extensions
            for (Map.Entry<String, Object> entry : sysHeaderMap.entrySet()) {
                String key = entry.getKey();
                if (key.equalsIgnoreCase(McpProtocolConstant.CONSTANTS_KEY_ID)
                        || key.equalsIgnoreCase(McpProtocolConstant.CONSTANTS_KEY_SOURCE)
                        || key.equalsIgnoreCase(McpProtocolConstant.CONSTANTS_KEY_TYPE)
                        || key.equalsIgnoreCase(McpProtocolConstant.CONSTANTS_KEY_SUBJECT)) {
                    continue;
                }
                builder.withExtension(key.toLowerCase(Locale.ROOT), entry.getValue().toString());
            }

            // Handle body
            byte[] requestBody = mcpEventWrapper.getBody();
            if (StringUtils.equals(dataContentType, McpProtocolConstant.CONSTANTS_APPLICATION_JSON)) {
                Map<String, Object> requestBodyMap = JsonUtils.parseTypeReferenceObject(
                        new String(requestBody, StandardCharsets.UTF_8),
                        new TypeReference<HashMap<String, Object>>() {}
                );

                Map<String, Object> data = new HashMap<>();
                data.put(McpProtocolConstant.CONSTANTS_KEY_HEADERS, requestHeaderMap);
                data.put(McpProtocolConstant.CONSTANTS_KEY_BODY, requestBodyMap);

                builder.withData(JsonUtils.toJSONString(data).getBytes(StandardCharsets.UTF_8));
            } else {
                builder.withData(requestBody);
            }

            return builder.build();
        } catch (Exception e) {
            throw new ProtocolHandleException("Failed to build CloudEvent from McpEventWrapper", e);
        }
    }

}
