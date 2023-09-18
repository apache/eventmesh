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

package org.apache.eventmesh.protocol.meshmessage.resolver.tcp;

import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.protocol.tcp.EventMeshMessage;
import org.apache.eventmesh.common.protocol.tcp.Header;
import org.apache.eventmesh.common.protocol.tcp.Package;
import org.apache.eventmesh.protocol.api.exception.ProtocolHandleException;
import org.apache.eventmesh.protocol.meshmessage.MeshMessageProtocolConstant;

import org.apache.commons.lang3.StringUtils;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import io.cloudevents.CloudEvent;
import io.cloudevents.SpecVersion;
import io.cloudevents.core.builder.CloudEventBuilder;

public class TcpMessageProtocolResolver {

    public static CloudEvent buildEvent(Header header, EventMeshMessage message) throws ProtocolHandleException {
        CloudEventBuilder cloudEventBuilder;

        String protocolType = header.getProperty(Constants.PROTOCOL_TYPE).toString();
        String protocolVersion = header.getProperty(Constants.PROTOCOL_VERSION).toString();
        String protocolDesc = header.getProperty(Constants.PROTOCOL_DESC).toString();

        if (StringUtils.isAnyBlank(protocolType, protocolVersion, protocolDesc)) {
            throw new ProtocolHandleException(String.format("invalid protocol params protocolType %s|protocolVersion %s|protocolDesc %s",
                    protocolType, protocolVersion, protocolDesc));
        }

        if (!StringUtils.equals(MeshMessageProtocolConstant.PROTOCOL_NAME, protocolType)) {
            throw new ProtocolHandleException(String.format("Unsupported protocolType: %s", protocolType));
        }

        String topic = message.getTopic();
        String content = message.getBody();

        if (StringUtils.equalsAny(protocolVersion, SpecVersion.V1.toString(), SpecVersion.V03.toString())) {
            cloudEventBuilder = CloudEventBuilder.fromSpecVersion(SpecVersion.parse(protocolVersion));
        } else {
            throw new ProtocolHandleException(String.format("Unsupported protocolVersion: %s", protocolVersion));
        }

        cloudEventBuilder.withId(header.getSeq())
                .withSource(URI.create("/"))
                .withType("eventmeshmessage")
                .withSubject(topic)
                .withData(content.getBytes(Constants.DEFAULT_CHARSET));

        if (message.getHeaders().containsKey(Constants.DATA_CONTENT_TYPE)) {
            cloudEventBuilder.withDataContentType(message.getHeaders().get(Constants.DATA_CONTENT_TYPE));
        }

        for (Map.Entry<String, Object> prop : header.getProperties().entrySet()) {
            try {
                cloudEventBuilder.withExtension(prop.getKey(), prop.getValue().toString());
            } catch (Exception e) {
                throw new ProtocolHandleException(String.format("Abnormal propKey: %s", prop.getKey()), e);
            }
        }

        for (Map.Entry<String, String> prop : message.getProperties().entrySet()) {
            try {
                cloudEventBuilder.withExtension(prop.getKey(), prop.getValue());
            } catch (Exception e) {
                throw new ProtocolHandleException(String.format("Abnormal propKey: %s", prop.getKey()), e);
            }
        }

        return cloudEventBuilder.build();

    }

    public static Package buildEventMeshMessage(CloudEvent cloudEvent) {
        EventMeshMessage eventMeshMessage = new EventMeshMessage();
        eventMeshMessage.setTopic(cloudEvent.getSubject());
        eventMeshMessage.setBody(new String(Objects.requireNonNull(cloudEvent.getData()).toBytes(), Constants.DEFAULT_CHARSET));

        Map<String, String> prop = new HashMap<>();
        cloudEvent.getExtensionNames().forEach(k -> {
            prop.put(k, Objects.requireNonNull(cloudEvent.getExtension(k)).toString());
        });
        eventMeshMessage.setProperties(prop);

        Package pkg = new Package();
        pkg.setBody(eventMeshMessage);

        if (!StringUtils.isBlank(cloudEvent.getDataContentType())) {
            eventMeshMessage.getHeaders().put(Constants.DATA_CONTENT_TYPE, cloudEvent.getDataContentType());
        }

        return pkg;
    }
}
