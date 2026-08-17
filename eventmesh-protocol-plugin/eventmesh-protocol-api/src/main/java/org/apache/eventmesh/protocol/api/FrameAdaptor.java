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

package org.apache.eventmesh.protocol.api;

import org.apache.eventmesh.common.protocol.ProtocolTransportObject;
import org.apache.eventmesh.common.wire.EventMeshFrame;
import org.apache.eventmesh.protocol.api.exception.ProtocolHandleException;
import org.apache.eventmesh.spi.EventMeshExtensionType;
import org.apache.eventmesh.spi.EventMeshSPI;

/**
 * SPI for converting between an <b>external protocol</b> (CloudEvents JSON, MeshMessage TCP Package,
 * gRPC, …) and EventMesh's internal {@link EventMeshFrame}. Each protocol plugin implements this so
 * the runtime calls a single SPI boundary — it never hardcodes {@code fromCloudEvent} /
 * {@code MeshMessageFrameCodec} directly.
 *
 * <p>This is the protocol-layer counterpart to the internal {@code WireCodec}: {@code WireCodec}
 * shapes the <i>internal MQ bytes</i> (EventMeshFrame ↔ byte[]); {@code FrameAdaptor} shapes the
 * <i>external protocol representation</i> (EventMeshFrame ↔ the client's wire format). The two
 * never overlap: CloudEvent and MeshMessage are peer external protocols, neither converts to the
 * other — both convert directly to/from EventMeshFrame.</p>
 *
 * <h3>Ingress (client → runtime)</h3>
 * <pre>{@code
 *   ProtocolTransportObject proto = ...; // what arrived on the wire (Package, byte[], HttpCommand)
 *   EventMeshFrame frame = adaptor.toFrame(proto);
 *   ingress.publish(topic, frame);
 * }</pre>
 *
 * <h3>Egress (runtime → client)</h3>
 * <pre>{@code
 *   EventMeshFrame frame = ...; // what the dispatcher is delivering
 *   ProtocolTransportObject proto = adaptor.fromFrame(frame);
 *   // write proto to the client's transport
 * }</pre>
 *
 * @since 1.11.0
 */
@EventMeshSPI(eventMeshExtensionType = EventMeshExtensionType.PROTOCOL)
public interface FrameAdaptor {

    /**
     * Convert an external-protocol transport object to the internal EventMeshFrame (ingress).
     *
     * @param proto the wire-format object ({@link org.apache.eventmesh.common.protocol.tcp.Package}
     *              for MeshMessage TCP, {@code byte[]} / {@link org.apache.eventmesh.common.protocol.http.HttpCommand}
     *              for CloudEvents HTTP, etc.)
     * @return the internal EventMeshFrame
     */
    EventMeshFrame toFrame(ProtocolTransportObject proto) throws ProtocolHandleException;

    /**
     * Convert an internal EventMeshFrame back to the external-protocol transport object (egress).
     *
     * @param frame the internal EventMeshFrame being delivered to a client
     * @return the wire-format object to write to the client's transport
     */
    ProtocolTransportObject fromFrame(EventMeshFrame frame) throws ProtocolHandleException;

    /**
     * The protocol type this adaptor handles (e.g. {@code "cloudevents"}, {@code "meshmessage"}).
     * Used by the runtime to select the right adaptor per client connection.
     */
    String getProtocolType();

    /**
     * Ingress convenience: convert without throwing — wraps {@link ProtocolHandleException} in a
     * {@link RuntimeException} so call sites that can't propagate checked exceptions stay terse.
     */
    default EventMeshFrame toFrameSilent(ProtocolTransportObject proto) {
        try {
            return toFrame(proto);
        } catch (ProtocolHandleException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Egress convenience: convert without throwing — wraps {@link ProtocolHandleException} in a
     * {@link RuntimeException}.
     */
    default ProtocolTransportObject fromFrameSilent(EventMeshFrame frame) {
        try {
            return fromFrame(frame);
        } catch (ProtocolHandleException e) {
            throw new RuntimeException(e);
        }
    }
}
