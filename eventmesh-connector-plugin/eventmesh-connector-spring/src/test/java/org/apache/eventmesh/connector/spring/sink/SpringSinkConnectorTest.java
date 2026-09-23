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

package org.apache.eventmesh.connector.spring.sink;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.Test;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

/**
 * Hermetic unit test of the {@link SpringSinkConnector} forwarding contract: put() without an
 * injected EventForwarder fails fast; with one, every event is forwarded in order and a failing
 * forwarder surfaces as a RuntimeException (redelivery).
 */
class SpringSinkConnectorTest {

    private static CloudEvent event(String id) {
        return CloudEventBuilder.v1().withId(id).withSource(java.net.URI.create("/test"))
            .withType("test.event").build();
    }

    @Test
    void putWithoutForwarderFailsFast() {
        SpringSinkConnector sink = new SpringSinkConnector();
        sink.init(new Properties());
        assertThrows(IllegalStateException.class,
            () -> sink.put(Collections.singletonList(event("e1"))));
    }

    @Test
    void putForwardsEveryEventInOrder() {
        SpringSinkConnector sink = new SpringSinkConnector();
        sink.init(new Properties());
        List<CloudEvent> seen = new CopyOnWriteArrayList<>();
        sink.setForwarder(seen::add);
        sink.put(Arrays.asList(event("e1"), event("e2"), event("e3")));
        assertEquals(Arrays.asList("e1", "e2", "e3"),
            seen.stream().map(CloudEvent::getId).toList());
    }

    @Test
    void putSurfacesForwarderFailure() {
        SpringSinkConnector sink = new SpringSinkConnector();
        sink.init(new Properties());
        sink.setForwarder(e -> {
            throw new IllegalArgumentException("boom");
        });
        RuntimeException ex = assertThrows(RuntimeException.class,
            () -> sink.put(Collections.singletonList(event("e1"))));
        assertTrue(ex.getMessage().contains("e1"), "error must name the failing event");
    }
}
