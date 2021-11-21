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

package org.apache.eventmesh.client.http.producer;

import org.apache.eventmesh.client.http.conf.LiteClientConfig;
import org.apache.eventmesh.common.EventMeshMessage;
import org.apache.eventmesh.common.exception.EventMeshException;

import io.cloudevents.CloudEvent;
import io.openmessaging.api.Message;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LiteProducer implements AutoCloseable {

    private final EventMeshMessageProducer eventMeshMessageProducer;
    private final CloudEventProducer       cloudEventProducer;
    private final OpenMessageProducer      openMessageProducer;

    public LiteProducer(final LiteClientConfig liteClientConfig) throws EventMeshException {
        this.cloudEventProducer = new CloudEventProducer(liteClientConfig);
        this.eventMeshMessageProducer = new EventMeshMessageProducer(liteClientConfig);
        this.openMessageProducer = new OpenMessageProducer(liteClientConfig);
    }

    public void publish(final EventMeshMessage message) throws EventMeshException {
        eventMeshMessageProducer.publish(message);
    }

    public void publish(final CloudEvent cloudEvent) throws EventMeshException {
        cloudEventProducer.publish(cloudEvent);
    }

    public void publish(final Message openMessage) throws EventMeshException {
        openMessageProducer.publish(openMessage);
    }

    public EventMeshMessage request(final EventMeshMessage message, final long timeout) throws EventMeshException {
        return eventMeshMessageProducer.request(message, timeout);
    }

    public CloudEvent request(final CloudEvent cloudEvent, final long timeout) throws EventMeshException {
        return cloudEventProducer.request(cloudEvent, timeout);
    }

    public Message request(final Message openMessage, final long timeout) throws EventMeshException {
        return openMessageProducer.request(openMessage, timeout);
    }

    public void request(final EventMeshMessage message, final RRCallback rrCallback, final long timeout)
        throws EventMeshException {
        eventMeshMessageProducer.request(message, rrCallback, timeout);
    }

    public void request(final CloudEvent cloudEvent, final RRCallback rrCallback, final long timeout)
        throws EventMeshException {
        cloudEventProducer.request(cloudEvent, rrCallback, timeout);
    }

    public void request(final Message openMessage, final RRCallback rrCallback, final long timeout)
        throws EventMeshException {
        openMessageProducer.request(openMessage, rrCallback, timeout);
    }

    @Override
    public void close() throws EventMeshException {
        try (
            final EventMeshMessageProducer ignored = eventMeshMessageProducer;
            final OpenMessageProducer ignored1 = openMessageProducer;
            final CloudEventProducer ignored2 = cloudEventProducer) {
            log.info("Close producer");
        }
    }
}
