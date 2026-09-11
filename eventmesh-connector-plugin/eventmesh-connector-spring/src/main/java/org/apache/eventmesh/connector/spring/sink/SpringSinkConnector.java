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

import org.apache.eventmesh.connector.SinkConnector;

import java.util.List;
import java.util.Properties;

import io.cloudevents.CloudEvent;

import lombok.extern.slf4j.Slf4j;

/**
 * New-architecture Spring sink connector: bridges EventMesh deliveries into a hosting Spring
 * application context.
 *
 * <p>The host Spring application injects an {@link EventForwarder} (e.g. an
 * ApplicationEventPublisher adapter bean) via {@link #setForwarder(EventForwarder)}. Until a
 * forwarder is injected, {@code put} fails fast with {@link IllegalStateException} so the
 * runtime does not ACK and EventMesh redelivers — events are never silently dropped.</p>
 */
@Slf4j
public class SpringSinkConnector implements SinkConnector {

    /** Bridge the host Spring context injects; typically wraps ApplicationEventPublisher. */
    public interface EventForwarder {

        /** Publish one CloudEvent into the Spring context; may throw to signal failure. */
        void forward(CloudEvent event);
    }

    private volatile EventForwarder forwarder;

    /** Called by the host Spring application to wire the publisher bridge. */
    public void setForwarder(EventForwarder forwarder) {
        this.forwarder = forwarder;
    }

    @Override
    public void init(Properties props) {
        log.info("spring sink initialized: waiting for EventForwarder injection from the Spring context");
    }

    @Override
    public void put(List<CloudEvent> events) {
        EventForwarder f = forwarder;
        if (f == null) {
            throw new IllegalStateException(
                "spring sink: no EventForwarder injected — call setForwarder() from the Spring context");
        }
        for (CloudEvent event : events) {
            try {
                f.forward(event);
            } catch (Exception e) {
                throw new RuntimeException("spring sink forward failed for event " + event.getId()
                    + ": " + e.getMessage(), e);
            }
        }
    }

    @Override
    public void commit(List<CloudEvent> written) {
        // The forwarder's successful publish is the write ack.
    }
}
