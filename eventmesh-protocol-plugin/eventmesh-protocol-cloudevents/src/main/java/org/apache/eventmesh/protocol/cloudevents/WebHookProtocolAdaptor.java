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

package org.apache.eventmesh.protocol.cloudevents;

import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.protocol.ProtocolTransportObject;
import org.apache.eventmesh.common.protocol.http.WebhookProtocolTransportObject;
import org.apache.eventmesh.protocol.api.ProtocolAdaptor;
import org.apache.eventmesh.protocol.api.exception.ProtocolHandleException;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

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
            .withExtension(Constants.PROTOCOL_TYPE, "http")
            .build();
    }

    @Override
    public List<CloudEvent> toBatchCloudEvent(WebhookProtocolTransportObject protocol) throws ProtocolHandleException {
        List<CloudEvent> cloudEventList = new ArrayList<CloudEvent>();
        return cloudEventList;
    }

    @Override
    public ProtocolTransportObject fromCloudEvent(CloudEvent cloudEvent) throws ProtocolHandleException {
        return null;
    }

    @Override
    public String getProtocolType() {
        return "webhookProtocolAdaptor";
    }

}
