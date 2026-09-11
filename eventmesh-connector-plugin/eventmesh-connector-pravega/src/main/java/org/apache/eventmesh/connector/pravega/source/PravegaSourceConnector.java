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

package org.apache.eventmesh.connector.pravega.source;

import org.apache.eventmesh.connector.SourceConnector;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.pravega.client.ClientConfig;
import io.pravega.client.EventStreamClientFactory;
import io.pravega.client.admin.ReaderGroupManager;
import io.pravega.client.stream.EventRead;
import io.pravega.client.stream.EventStreamReader;
import io.pravega.client.stream.ReaderGroupConfig;
import io.pravega.client.stream.ReinitializationRequiredException;
import io.pravega.client.stream.Serializer;
import io.pravega.client.stream.impl.ByteArraySerializer;
import io.pravega.shared.NameUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * New-architecture Pravega source connector: reads events from a Pravega stream through an
 * {@link EventStreamReader} (reader-group managed offsets), ported from the master-branch
 * openconnect implementation. {@link #commit(CloudEvent)} is a no-op because the reader group
 * checkpoints offsets natively.
 */
@Slf4j
public class PravegaSourceConnector implements SourceConnector {

    private String scope;
    private String streamName;
    private String controllerUri;
    private long readTimeoutMs;

    private EventStreamClientFactory clientFactory;
    private ReaderGroupManager readerGroupManager;
    private EventStreamReader<byte[]> reader;

    @Override
    public void init(Properties props) {
        this.scope = props.getProperty("connector.scope", "scope");
        this.streamName = props.getProperty("connector.stream", "stream");
        this.controllerUri = props.getProperty("connector.controllerUri", "tcp://localhost:9090");
        this.readTimeoutMs = Long.parseLong(props.getProperty("connector.readTimeoutMs", "1000"));
    }

    private synchronized void ensureStarted() {
        if (reader != null) {
            return;
        }
        ClientConfig clientConfig = ClientConfig.builder().controllerURI(URI.create(controllerUri)).build();
        String stream = NameUtils.getScopedStreamName(scope, streamName);
        clientFactory = EventStreamClientFactory.withScope(scope, clientConfig);
        readerGroupManager = ReaderGroupManager.withScope(scope, clientConfig);
        readerGroupManager.createReaderGroup(streamName,
            ReaderGroupConfig.builder().stream(stream).build());
        Serializer<byte[]> serializer = new ByteArraySerializer();
        reader = clientFactory.createReader("eventmesh-pravega-source", streamName, serializer,
            io.pravega.client.stream.ReaderConfig.builder().build());
        log.info("pravega source started: {}/{} @ {}", scope, streamName, controllerUri);
    }

    @Override
    public List<CloudEvent> poll() {
        ensureStarted();
        List<CloudEvent> out = new ArrayList<>();
        try {
            long deadline = System.currentTimeMillis() + readTimeoutMs;
            while (System.currentTimeMillis() < deadline) {
                EventRead<byte[]> read = reader.readNextEvent(readTimeoutMs);
                byte[] payload = read.getEvent();
                if (payload == null) {
                    break;
                }
                CloudEvent event = CloudEventBuilder.v1()
                    .withId("pravega-" + read.getPosition())
                    .withSource(URI.create("pravega:" + scope + "/" + streamName))
                    .withType("pravega.event")
                    .withSubject(streamName)
                    .withDataContentType("application/octet-stream")
                    .withData(payload)
                    .build();
                out.add(event);
            }
        } catch (ReinitializationRequiredException e) {
            // reader group rebalanced: recreate the reader on the next poll
            closeReader();
            log.warn("pravega source reader reinitialization required: {}", e.toString());
        }
        return out;
    }

    private synchronized void closeReader() {
        if (reader != null) {
            try {
                reader.close();
            } catch (Exception ignored) {
                // best-effort
            }
            reader = null;
        }
    }

    @Override
    public void commit(CloudEvent lastPublished) {
        // Reader-group managed offsets: Pravega checkpoints natively, nothing to do here.
    }
}
