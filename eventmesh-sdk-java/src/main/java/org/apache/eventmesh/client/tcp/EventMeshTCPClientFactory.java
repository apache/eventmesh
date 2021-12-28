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

package org.apache.eventmesh.client.tcp;

import org.apache.eventmesh.client.tcp.conf.EventMeshTCPClientConfig;
import org.apache.eventmesh.client.tcp.impl.cloudevent.CloudEventTCPClient;
import org.apache.eventmesh.client.tcp.impl.eventmeshmessage.EventMeshMessageTCPClient;
import org.apache.eventmesh.client.tcp.impl.openmessage.OpenMessageTCPClient;
import org.apache.eventmesh.common.protocol.tcp.EventMeshMessage;

import com.google.common.base.Preconditions;

import io.cloudevents.CloudEvent;
import io.openmessaging.api.Message;
import lombok.experimental.UtilityClass;

@UtilityClass
public class EventMeshTCPClientFactory {

    /**
     * Create target {@link EventMeshTCPClient}.
     *
     * @param eventMeshTcpClientConfig client config
     * @param protocolMessageClass     target message protocol class
     * @param <ProtocolMessage>        target message protocol type
     * @return Target client
     */
    @SuppressWarnings("unchecked")
    public static <ProtocolMessage> EventMeshTCPClient<ProtocolMessage> createEventMeshTCPClient(
        EventMeshTCPClientConfig eventMeshTcpClientConfig, Class<ProtocolMessage> protocolMessageClass) {
        Preconditions.checkNotNull(protocolMessageClass, "ProtocolMessage type cannot be null");
        Preconditions.checkNotNull(eventMeshTcpClientConfig, "EventMeshTcpClientConfig cannot be null");

        if (protocolMessageClass.isAssignableFrom(EventMeshMessage.class)) {
            return (EventMeshTCPClient<ProtocolMessage>) new EventMeshMessageTCPClient(eventMeshTcpClientConfig);
        }
        if (protocolMessageClass.isAssignableFrom(CloudEvent.class)) {
            return (EventMeshTCPClient<ProtocolMessage>) new CloudEventTCPClient(eventMeshTcpClientConfig);
        }
        if (protocolMessageClass.isAssignableFrom(Message.class)) {
            return (EventMeshTCPClient<ProtocolMessage>) new OpenMessageTCPClient(eventMeshTcpClientConfig);
        }
        throw new IllegalArgumentException(
            String.format("ProtocolMessageClass: %s is not supported", protocolMessageClass));
    }
}
