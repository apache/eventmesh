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

package org.apache.eventmesh.connector.pravega.sink;

import org.apache.eventmesh.connector.SinkConnector;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;

import io.cloudevents.CloudEvent;
import io.pravega.client.ClientConfig;
import io.pravega.client.EventStreamClientFactory;
import io.pravega.client.admin.StreamManager;
import io.pravega.client.stream.EventStreamWriter;
import io.pravega.client.stream.Serializer;
import io.pravega.client.stream.StreamConfiguration;
import io.pravega.client.stream.impl.ByteArraySerializer;
import io.pravega.shared.NameUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * New-architecture Pravega sink connector: writes CloudEvents into a Pravega stream through an
 * {@link EventStreamWriter}, ported from the master-branch openconnect implementation. Events
 * are flushed transactionally on {@link #commit(List)}: a failure in {@code put} aborts the
 * transaction so the runtime redelivers (at-least-once).
 */
@Slf4j
public class PravegaSinkConnector implements SinkConnector {

    private String scope;
    private String streamName;
    private String controllerUri;
    private long txnTimeoutMs;

    private EventStreamClientFactory clientFactory;
    private StreamManager streamManager;
    private EventStreamWriter<byte[]> writer;

    @Override
    public void init(Properties props) {
        this.scope = props.getProperty("connector.scope", "scope");
        this.streamName = props.getProperty("connector.stream", "stream");
        this.controllerUri = props.getProperty("controllerUri", "tcp://localhost:9090");
        this.txnTimeoutMs = Long.parseLong(props.getProperty("connector.txnTimeoutMs", "30000"));
    }

    private synchronized void ensureStarted() {
        if (writer != null) {
            return;
        }
        ClientConfig clientConfig = ClientConfig.builder().controllerURI(URI.create(controllerUri)).build();
        String qualified = NameUtils.getScopedStreamName(scope, streamName);
        streamManager = StreamManager.create(clientConfig);
        streamManager.createScope(scope);
        streamManager.createStream(scope, streamName, StreamConfiguration.builder().build());
        clientFactory = EventStreamClientFactory.withScope(scope, clientConfig);
        Serializer<byte[]> serializer = new ByteArraySerializer();
        writer = clientFactory.createEventWriter(streamName, serializer,
            io.pravega.client.stream.EventWriterConfig.builder().transactionTimeoutTime(txnTimeoutMs).build());
        log.info("pravega sink started: {}/{} @ {}", scope, streamName, controllerUri);
    }

    @Override
    public void put(List<CloudEvent> events) {
        ensureStarted();
        // write-then-flush: each event is routed by its id; a write failure throws so the
        // runtime does not ACK (redelivery).
        List<CompletableFuture<Void>> futures = new ArrayList<>(events.size());
        try {
            for (CloudEvent event : events) {
                byte[] data = event.getData() != null ? event.getData().toBytes() : new byte[0];
                futures.add(writer.writeEvent(event.getId(), data));
            }
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        } catch (Exception e) {
            throw new RuntimeException("pravega sink write failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void commit(List<CloudEvent> written) {
        // EventStreamWriter is durable per write; nothing further to checkpoint.
    }
}
