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

package org.apache.eventmesh.protocol.amqp;

import io.cloudevents.CloudEvent;
import org.apache.eventmesh.common.protocol.ProtocolTransportObject;
import org.apache.eventmesh.common.protocol.amqp.AmqpMessage;
import org.apache.eventmesh.protocol.amqp.resolver.AmqpProtocolResolver;
import org.apache.eventmesh.protocol.api.ProtocolAdaptor;
import org.apache.eventmesh.protocol.api.exception.ProtocolHandleException;

import java.util.List;

/**
 * Conversion between amqp message to cloudEvent
 */
public class AmqpProtocolAdaptor implements ProtocolAdaptor<ProtocolTransportObject> {
    @Override
    public CloudEvent toCloudEvent(ProtocolTransportObject protocol) throws ProtocolHandleException {
        if (protocol instanceof AmqpMessage) {
            AmqpMessage amqpMessage = (AmqpMessage) protocol;
            return deserializeAmqpProtocol(amqpMessage);

        } else {
            throw new ProtocolHandleException(String.format("protocol class: %s", protocol.getClass()));
        }
    }

    private CloudEvent deserializeAmqpProtocol(AmqpMessage amqpMessage)
            throws ProtocolHandleException {
        return AmqpProtocolResolver.buildEvent(amqpMessage);
    }

    @Override
    public List<CloudEvent> toBatchCloudEvent(ProtocolTransportObject protocol) throws ProtocolHandleException {
        return null;
    }

    @Override
    public ProtocolTransportObject fromCloudEvent(CloudEvent cloudEvent) throws ProtocolHandleException {
        String type = cloudEvent.getType();
        if (AMQPProtocolConstant.PROTOCOL_NAME.equalsIgnoreCase(type)) {
            return AmqpProtocolResolver.buildAmqpMessage(cloudEvent);
        } else {
            throw new ProtocolHandleException(String.format("Unsupported protocolDesc: %s", type));
        }
    }

    @Override
    public String getProtocolType() {
        return null;
    }
}