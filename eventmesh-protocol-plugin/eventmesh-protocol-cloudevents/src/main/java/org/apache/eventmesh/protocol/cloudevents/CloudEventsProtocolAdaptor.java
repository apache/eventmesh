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

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import org.apache.commons.lang3.StringUtils;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.command.HttpCommand;
import org.apache.eventmesh.common.protocol.http.body.Body;
import org.apache.eventmesh.common.protocol.tcp.Header;
import org.apache.eventmesh.common.protocol.tcp.Package;
import org.apache.eventmesh.protocol.api.ProtocolAdaptor;

import org.apache.eventmesh.protocol.api.exception.ProtocolHandleException;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * CloudEvents protocol adaptor, used to transform CloudEvents message to CloudEvents message.
 *
 * @since 1.3.0
 */
public class CloudEventsProtocolAdaptor<T> implements ProtocolAdaptor<T> {

    @Override
    public CloudEvent toCloudEvent(T cloudEvent) throws ProtocolHandleException {

        CloudEventBuilder cloudEventBuilder;

        if (cloudEvent instanceof Package) {

            Header header = ((Package) cloudEvent).getHeader();
            Object body = ((Package) cloudEvent).getBody();
            String protocolType = header.getProperty(Constants.PROTOCOL_TYPE).toString();
            String protocolVersion = header.getProperty(Constants.PROTOCOL_VERSION).toString();
            String protocolDesc = header.getProperty(Constants.PROTOCOL_DESC).toString();

            if (StringUtils.isBlank(protocolType)
                    || StringUtils.isBlank(protocolVersion)
                    || StringUtils.isBlank(protocolDesc)) {
                throw new ProtocolHandleException(String.format("invalid protocol params protocolType %s|protocolVersion %s|protocolDesc %s",
                        protocolType, protocolVersion, protocolDesc));
            }

            if (!StringUtils.equals("cloudevents", protocolType)) {
                throw new ProtocolHandleException(String.format("Unsupported protocolType: %s", protocolType));
            }
            if (StringUtils.equals("1.0", protocolVersion)) {
                cloudEventBuilder = CloudEventBuilder.v1((CloudEvent) body);

                for (String propKey : header.getProperties().keySet()) {
                    cloudEventBuilder.withExtension(propKey, header.getProperty(propKey).toString());
                }

                return cloudEventBuilder.build();

            } else if (StringUtils.equals("0.3", protocolVersion)) {
                cloudEventBuilder = CloudEventBuilder.v03((CloudEvent) body);

                for (String propKey : header.getProperties().keySet()) {
                    cloudEventBuilder.withExtension(propKey, header.getProperty(propKey).toString());
                }

                return cloudEventBuilder.build();

            } else {
                throw new ProtocolHandleException(String.format("Unsupported protocolVersion: %s", protocolVersion));
            }
        } else if (cloudEvent instanceof HttpCommand) {
            org.apache.eventmesh.common.protocol.http.header.Header header = ((HttpCommand) cloudEvent).getHeader();
            Body body = ((HttpCommand) cloudEvent).getBody();
            //todo:convert httpCommand to cloudevents
        } else {
            throw new ProtocolHandleException("protocol class: " + cloudEvent.getClass());
        }
        return null;
    }

    @Override
    public List<CloudEvent> toBatchCloudEvent(T protocol) throws ProtocolHandleException {
        return null;
    }

    @Override
    public Object fromCloudEvent(CloudEvent cloudEvent) throws ProtocolHandleException {
        String protocolDesc = cloudEvent.getExtension(Constants.PROTOCOL_DESC).toString();
        if (StringUtils.equals("http", protocolDesc)) {
            return new String(cloudEvent.getData().toBytes(), StandardCharsets.UTF_8);
        } else if (StringUtils.equals("tcp", protocolDesc)) {
            Package pkg = new Package();
            pkg.setBody(cloudEvent);
            return pkg;
        } else {
            throw new ProtocolHandleException(String.format("Unsupported protocolDesc: %s", protocolDesc));
        }

    }

    @Override
    public String getProtocolType() {
        return CloudEventsProtocolConstant.PROTOCOL_NAME;
    }
}
