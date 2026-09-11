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

package org.apache.eventmesh.connector.canal.source;

import org.apache.eventmesh.connector.SourceConnector;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

import com.alibaba.otter.canal.client.CanalConnector;
import com.alibaba.otter.canal.client.CanalConnectors;
import com.alibaba.otter.canal.protocol.CanalEntry;
import com.alibaba.otter.canal.protocol.Message;

import lombok.extern.slf4j.Slf4j;

/**
 * New-architecture canal source connector: consumes MySQL binlog entries from a deployed canal
 * server over the canal TCP protocol and converts each row change to a CloudEvent.
 *
 * <p>External-system logic ported from the master-branch (openconnect) canal connector; the
 * at-least-once checkpoint is the canal batch ack: {@link #poll()} fetches without ack and
 * {@link #commit(CloudEvent)} acks the pending batch only after EventMesh accepted the publish.</p>
 */
@Slf4j
public class CanalSourceConnector implements SourceConnector {

    private String canalHost;
    private int canalPort;
    private String destination;
    private String username;
    private String password;
    private String subscribeFilter;
    private int batchSize;
    private long pollTimeoutMs;

    private CanalConnector connector;
    private long pendingBatchId = -1L;

    @Override
    public void init(Properties props) {
        this.canalHost = props.getProperty("connector.canalHost", "localhost");
        this.canalPort = Integer.parseInt(props.getProperty("connector.canalPort", "11111"));
        this.destination = props.getProperty("connector.destination", "example");
        this.username = props.getProperty("connector.username", "");
        this.password = props.getProperty("connector.password", "");
        this.subscribeFilter = props.getProperty("connector.subscribeFilter", ".*\\..*");
        this.batchSize = Integer.parseInt(props.getProperty("connector.batchSize", "100"));
        this.pollTimeoutMs = Long.parseLong(props.getProperty("connector.pollTimeoutMs", "1000"));
    }

    private synchronized void ensureStarted() {
        if (connector == null) {
            connector = CanalConnectors.newSingleConnector(
                new InetSocketAddress(canalHost, canalPort), destination, username, password);
            connector.connect();
            connector.subscribe(subscribeFilter);
            connector.rollback();
            log.info("canal source connected: {}:{}/{} filter={}", canalHost, canalPort, destination, subscribeFilter);
        }
    }

    @Override
    public List<CloudEvent> poll() {
        ensureStarted();
        List<CloudEvent> out = new ArrayList<>();
        try {
            Message message = connector.getWithoutAck(batchSize);
            long batchId = message.getId();
            if (batchId != -1 && !message.getEntries().isEmpty()) {
                pendingBatchId = batchId;
                for (CanalEntry.Entry entry : message.getEntries()) {
                    if (entry.getEntryType() != CanalEntry.EntryType.ROWDATA) {
                        continue;
                    }
                    String position = entry.getHeader().getLogfileName() + ":" + entry.getHeader().getLogfileOffset();
                    CloudEvent event = CloudEventBuilder.v1()
                        .withId("canal-" + batchId + "-" + entry.getHeader().getLogfileOffset())
                        .withSource(URI.create("canal:" + destination))
                        .withType("canal.binlog." + entry.getHeader().getEventType().name().toLowerCase())
                        .withSubject(position)
                        .withDataContentType("application/octet-stream")
                        .withData(entry.getStoreValue().toByteArray())
                        .build();
                    out.add(event);
                }
            } else if (batchId != -1) {
                // empty heartbeat batch: ack immediately so the server does not stall
                connector.ack(batchId);
            }
        } catch (Exception e) {
            log.warn("canal source poll failed: {}", e.toString());
        }
        return out;
    }

    @Override
    public void commit(CloudEvent lastPublished) {
        // At-least-once: ack the canal batch only after EventMesh accepted the publish.
        synchronized (this) {
            if (connector != null && pendingBatchId != -1L) {
                try {
                    connector.ack(pendingBatchId);
                } catch (Exception e) {
                    log.warn("canal source ack failed for batch {}: {}", pendingBatchId, e.toString());
                }
                pendingBatchId = -1L;
            }
        }
    }
}
