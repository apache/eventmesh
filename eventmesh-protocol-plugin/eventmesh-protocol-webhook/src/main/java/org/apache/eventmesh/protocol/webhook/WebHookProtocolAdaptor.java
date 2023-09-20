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

package org.apache.eventmesh.protocol.webhook;

import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.protocol.ProtocolTransportObject;
import org.apache.eventmesh.common.protocol.http.HttpEventWrapper;
import org.apache.eventmesh.common.protocol.http.WebhookProtocolTransportObject;
import org.apache.eventmesh.common.utils.RandomStringUtils;
import org.apache.eventmesh.protocol.api.ProtocolAdaptor;
import org.apache.eventmesh.protocol.api.exception.ProtocolHandleException;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

public class WebHookProtocolAdaptor implements ProtocolAdaptor<WebhookProtocolTransportObject> {

    @Override
    public CloudEvent toCloudEvent(WebhookProtocolTransportObject protocol) throws ProtocolHandleException {
        return CloudEventBuilder.v1()
            .withId(protocol.getCloudEventId())
            .withSubject(protocol.getCloudEventName())
            .withSource(URI.create(protocol.getCloudEventSource()))
            .withDataContentType(protocol.getDataContentType())
            .withType(protocol.getEventType())
            .withData(protocol.getBody())
            .withExtension(Constants.PROTOCOL_TYPE, "webhook")
            .withExtension("bizseqno", RandomStringUtils.generateNum(30))
            .withExtension("uniqueid", RandomStringUtils.generateNum(30))
            .build();
    }

    @Override
    public List<CloudEvent> toBatchCloudEvent(WebhookProtocolTransportObject protocol) {
        return new ArrayList<>();
    }

    @Override
    public ProtocolTransportObject fromCloudEvent(CloudEvent cloudEvent) {
        final HttpEventWrapper httpEventWrapper = new HttpEventWrapper();
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
        sysHeaderMap.put("cloudEventId", cloudEvent.getId());
        sysHeaderMap.put("cloudEventName", cloudEvent.getSubject());
        sysHeaderMap.put("cloudEventSource", cloudEvent.getSource().toString());
        sysHeaderMap.put("type", cloudEvent.getType());
        httpEventWrapper.setSysHeaderMap(sysHeaderMap);
        if (cloudEvent.getData() != null) {
            httpEventWrapper.setBody(cloudEvent.getData().toBytes());
        }
        return httpEventWrapper;
    }

    @Override
    public String getProtocolType() {
        return "webhookProtocolAdaptor";
    }

}
