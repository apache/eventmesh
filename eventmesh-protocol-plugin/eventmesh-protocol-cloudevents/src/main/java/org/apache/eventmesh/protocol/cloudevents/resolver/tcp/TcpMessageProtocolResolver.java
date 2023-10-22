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

package org.apache.eventmesh.protocol.cloudevents.resolver.tcp;

import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.protocol.tcp.Header;
import org.apache.eventmesh.protocol.api.exception.ProtocolHandleException;
import org.apache.eventmesh.protocol.cloudevents.CloudEventsProtocolConstant;

import org.apache.commons.lang3.StringUtils;

import java.nio.charset.StandardCharsets;

import io.cloudevents.CloudEvent;
import io.cloudevents.SpecVersion;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.cloudevents.core.format.EventFormat;
import io.cloudevents.core.provider.EventFormatProvider;
import io.cloudevents.jackson.JsonFormat;

import com.google.common.base.Preconditions;

public class TcpMessageProtocolResolver {

    public static CloudEvent buildEvent(Header header, String cloudEventJson)
        throws ProtocolHandleException {
        final CloudEventBuilder cloudEventBuilder;

        String protocolType = header.getProperty(Constants.PROTOCOL_TYPE).toString();
        String protocolVersion = header.getProperty(Constants.PROTOCOL_VERSION).toString();
        String protocolDesc = header.getProperty(Constants.PROTOCOL_DESC).toString();

        if (StringUtils.isAnyBlank(protocolType, protocolVersion, protocolDesc)) {
            throw new ProtocolHandleException(
                String.format("invalid protocol params protocolType %s|protocolVersion %s|protocolDesc %s",
                    protocolType, protocolVersion, protocolDesc));
        }

        if (StringUtils.isBlank(cloudEventJson)) {
            throw new ProtocolHandleException(
                String.format("invalid method params cloudEventJson %s", cloudEventJson));
        }

        if (!StringUtils.equals(CloudEventsProtocolConstant.PROTOCOL_NAME, protocolType)) {
            throw new ProtocolHandleException(String.format("Unsupported protocolType: %s", protocolType));
        }

        if (StringUtils.equalsAny(protocolVersion, SpecVersion.V1.toString(), SpecVersion.V03.toString())) {
            // todo:resolve different format
            EventFormat eventFormat = EventFormatProvider.getInstance().resolveFormat(JsonFormat.CONTENT_TYPE);
            Preconditions
                .checkNotNull(eventFormat, "EventFormat: %s is not supported", JsonFormat.CONTENT_TYPE);
            CloudEvent event = eventFormat.deserialize(cloudEventJson.getBytes(StandardCharsets.UTF_8));
            cloudEventBuilder = CloudEventBuilder.from(event);
            header.getProperties().forEach((k, v) -> {
                cloudEventBuilder.withExtension(k, v.toString());
            });
            return cloudEventBuilder.build();
        } else {
            throw new ProtocolHandleException(String.format("Unsupported protocolVersion: %s", protocolVersion));
        }
    }
}
