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

package org.apache.eventmesh.runtime.transport.tcp;

import org.apache.eventmesh.common.protocol.tcp.Command;
import org.apache.eventmesh.common.protocol.tcp.Package;
import org.apache.eventmesh.protocol.api.exception.ProtocolHandleException;
import org.apache.eventmesh.protocol.meshmessage.MeshMessageProtocolAdaptor;

import io.cloudevents.CloudEvent;

/**
 * Production {@link PackageRouter} backed by the legacy {@link MeshMessageProtocolAdaptor}, so a
 * real legacy TCP client's frames are translated to/from CloudEvents with full fidelity (no stub).
 *
 * <p>Ingress: {@code ASYNC_MESSAGE_TO_SERVER} / {@code BROADCAST_MESSAGE_TO_SERVER} →
 * {@code adaptor.toCloudEvent(pkg)} → publish; the topic is the CloudEvent {@code subject} (the
 * resolver's bidirectional mapping). {@code ASYNC_MESSAGE_TO_CLIENT_ACK} → resolves the egress
 * delivery by id (echoed in the header property).</p>
 */
public class MeshMessagePackageRouter implements PackageRouter {

    private final MeshMessageProtocolAdaptor adaptor = new MeshMessageProtocolAdaptor();

    @Override
    public TcpRequest route(Package pkg) {
        Command cmd = pkg.getHeader() != null ? pkg.getHeader().getCommand() : null;
        if (cmd == Command.ASYNC_MESSAGE_TO_SERVER || cmd == Command.BROADCAST_MESSAGE_TO_SERVER) {
            try {
                CloudEvent event = adaptor.toCloudEvent(pkg);
                if (event == null) {
                    return null;
                }
                String topic = event.getSubject() != null ? event.getSubject() : "default";
                return TcpRequest.publish(topic, event);
            } catch (ProtocolHandleException e) {
                throw new IllegalArgumentException("tcp publish decode failed", e);
            }
        }
        if (cmd == Command.ASYNC_MESSAGE_TO_CLIENT_ACK) {
            String deliveryId = pkg.getHeader() != null
                ? pkg.getHeader().getStringProperty(NettyTcpPushChannel.HEADER_DELIVERY_ID) : null;
            return deliveryId != null ? TcpRequest.ack(deliveryId) : null;
        }
        return null;
    }
}
