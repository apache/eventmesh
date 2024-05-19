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

package org.apache.eventmesh.protocol.http;

import static org.apache.eventmesh.protocol.http.HttpProtocolConstant.CONSTANTS_KEY_BODY;
import static org.apache.eventmesh.protocol.http.HttpProtocolConstant.CONSTANTS_KEY_HEADERS;
import static org.apache.eventmesh.protocol.http.HttpProtocolConstant.CONSTANTS_KEY_METHOD;
import static org.apache.eventmesh.protocol.http.HttpProtocolConstant.CONSTANTS_KEY_PATH;

import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.protocol.ProtocolTransportObject;
import org.apache.eventmesh.common.protocol.http.HttpEventWrapper;
import org.apache.eventmesh.common.protocol.http.common.RequestURI;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.protocol.api.ProtocolAdaptor;
import org.apache.eventmesh.protocol.api.exception.ProtocolHandleException;
import org.apache.eventmesh.protocol.http.resolver.HttpRequestProtocolResolver;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import io.cloudevents.CloudEvent;

import com.fasterxml.jackson.core.type.TypeReference;

/**
 * CloudEvents protocol adaptor, used to transform CloudEvents message to CloudEvents message.
 *
 * @since 1.3.0
 */
public class HttpProtocolAdaptor<T extends ProtocolTransportObject>
    implements ProtocolAdaptor<ProtocolTransportObject> {

    @Override
    public CloudEvent toCloudEvent(ProtocolTransportObject protocolTransportObject) throws ProtocolHandleException {

        if (protocolTransportObject instanceof HttpEventWrapper) {
            HttpEventWrapper httpEventWrapper = (HttpEventWrapper) protocolTransportObject;
            String requestURI = httpEventWrapper.getRequestURI();

            return deserializeProtocol(requestURI, httpEventWrapper);

        } else {
            throw new ProtocolHandleException(String.format("protocol class: %s", protocolTransportObject.getClass()));
        }
    }

    private CloudEvent deserializeProtocol(String requestURI, HttpEventWrapper httpEventWrapper) throws ProtocolHandleException {

        if (requestURI.startsWith(RequestURI.PUBLISH.getRequestURI()) || requestURI.startsWith(RequestURI.PUBLISH_BRIDGE.getRequestURI())) {
            return HttpRequestProtocolResolver.buildEvent(httpEventWrapper);
        } else {
            throw new ProtocolHandleException(String.format("unsupported requestURI: %s", requestURI));
        }

    }

    @Override
    public List<CloudEvent> toBatchCloudEvent(ProtocolTransportObject protocol)
        throws ProtocolHandleException {
        return Collections.emptyList();
    }

    @Override
    public ProtocolTransportObject fromCloudEvent(CloudEvent cloudEvent) throws ProtocolHandleException {
        HttpEventWrapper httpEventWrapper = new HttpEventWrapper();
        Map<String, Object> sysHeaderMap = new HashMap<>();
        // ce attributes
        Set<String> attributeNames = cloudEvent.getAttributeNames();
        // ce extensions
        Set<String> extensionNames = cloudEvent.getExtensionNames();
        for (String attributeName : attributeNames) {
            sysHeaderMap.put(attributeName, cloudEvent.getAttribute(attributeName));
        }
        for (String extensionName : extensionNames) {
            sysHeaderMap.put(extensionName, cloudEvent.getExtension(extensionName));
        }
        httpEventWrapper.setSysHeaderMap(sysHeaderMap);
        // ce data
        if (cloudEvent.getData() != null) {
            Map<String, Object> dataContentMap = JsonUtils.parseTypeReferenceObject(
                new String(Objects.requireNonNull(cloudEvent.getData()).toBytes(), Constants.DEFAULT_CHARSET),
                new TypeReference<Map<String, Object>>() {
                });
            String requestHeader = JsonUtils.toJSONString(
                Objects.requireNonNull(dataContentMap, "Headers must not be null").get(CONSTANTS_KEY_HEADERS));
            byte[] requestBody = Objects.requireNonNull(
                JsonUtils.toJSONString(dataContentMap.get(CONSTANTS_KEY_BODY)), "Body must not be null").getBytes(StandardCharsets.UTF_8);
            Map<String, Object> requestHeaderMap = JsonUtils.parseTypeReferenceObject(requestHeader, new TypeReference<Map<String, Object>>() {
            });
            String requestURI = dataContentMap.get(CONSTANTS_KEY_PATH).toString();
            String httpMethod = dataContentMap.get(CONSTANTS_KEY_METHOD).toString();

            httpEventWrapper.setHeaderMap(requestHeaderMap);
            httpEventWrapper.setBody(requestBody);
            httpEventWrapper.setRequestURI(requestURI);
            httpEventWrapper.setHttpMethod(httpMethod);
        }
        return httpEventWrapper;

    }

    @Override
    public String getProtocolType() {
        return HttpProtocolConstant.PROTOCOL_NAME;
    }
}
