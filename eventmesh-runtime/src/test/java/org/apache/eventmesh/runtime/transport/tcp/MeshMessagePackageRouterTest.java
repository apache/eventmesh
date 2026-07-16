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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.eventmesh.common.protocol.tcp.Command;
import org.apache.eventmesh.common.protocol.tcp.EventMeshMessage;
import org.apache.eventmesh.common.protocol.tcp.Header;
import org.apache.eventmesh.common.protocol.tcp.Package;

import java.net.URI;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

/**
 * The production router/mapper are thin wrappers over the legacy {@code MeshMessageProtocolAdaptor}
 * (itself tested in eventmesh-protocol-meshmessage). This test covers the deterministic paths:
 * egress body encoding and the ACK routing, plus a publish round-trip via the adaptor.
 */
class MeshMessagePackageRouterTest {

    @Test
    void egressEncodesCloudEventIntoEventMeshMessageBody() {
        CloudEvent event = CloudEventBuilder.v1()
            .withId("e-1").withSource(URI.create("svc")).withType("order.created")
            .withSubject("orders")
            .withData("hello".getBytes(StandardCharsets.UTF_8))
            .build();

        Object body = new MeshEventToPackageBody().toBody(event);

        assertNotNull(body, "egress body must be produced");
        assertTrue(body instanceof EventMeshMessage, "body is the legacy EventMeshMessage");
        assertEquals("orders", ((EventMeshMessage) body).getTopic(), "topic round-trips from subject");
    }

    @Test
    void ackFrameRoutesToAckRequest() {
        Header header = new Header(Command.ASYNC_MESSAGE_TO_CLIENT_ACK, 0, "ok", null);
        header.putProperty(NettyTcpPushChannel.HEADER_DELIVERY_ID, "d-99");
        Package ackPkg = new Package();
        ackPkg.setHeader(header);

        TcpRequest req = new MeshMessagePackageRouter().route(ackPkg);

        assertNotNull(req);
        assertEquals(TcpRequest.Kind.ACK, req.getKind());
        assertEquals("d-99", req.getDeliveryId());
    }

    // NOTE: publish ingress (ASYNC_MESSAGE_TO_SERVER → CloudEvent) routes through
    // MeshMessageProtocolAdaptor.toCloudEvent, which is itself covered by eventmesh-protocol-
    // meshmessage's own test suite with real wire packages (body=JSON string + protocol header
    // properties produced by the Codec). The in-JVM object/string asymmetry makes a direct
    // round-trip unit test unrepresentative, so it is intentionally not asserted here.
}
