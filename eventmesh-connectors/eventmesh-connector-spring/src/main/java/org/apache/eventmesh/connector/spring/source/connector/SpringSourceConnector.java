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

package org.apache.eventmesh.connector.spring.source.connector;

import org.apache.eventmesh.connector.spring.source.MessageSendingOperations;
import org.apache.eventmesh.connector.spring.source.config.SpringSourceConfig;
import org.apache.eventmesh.openconnect.api.config.Config;
import org.apache.eventmesh.openconnect.api.connector.ConnectorContext;
import org.apache.eventmesh.openconnect.api.connector.SourceConnectorContext;
import org.apache.eventmesh.openconnect.api.source.Source;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.RecordOffset;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.RecordPartition;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SpringSourceConnector implements Source, MessageSendingOperations {

    private static final int DEFAULT_BATCH_SIZE = 10;

    private SpringSourceConfig sourceConfig;

    private BlockingQueue<ConnectRecord> queue;

    @Override
    public Class<? extends Config> configClass() {
        return SpringSourceConfig.class;
    }

    @Override
    public void init(Config config) throws Exception {
        // init config for spring source connector
        this.sourceConfig = (SpringSourceConfig) config;
        this.queue = new LinkedBlockingQueue<>(1000);
    }

    @Override
    public void init(ConnectorContext connectorContext) throws Exception {
        SourceConnectorContext sourceConnectorContext = (SourceConnectorContext) connectorContext;
        // init config for spring source connector
        this.sourceConfig = (SpringSourceConfig) sourceConnectorContext.getSourceConfig();
        this.queue = new LinkedBlockingQueue<>(1000);
    }

    @Override
    public void start() throws Exception {

    }

    @Override
    public void commit(ConnectRecord record) {

    }

    @Override
    public String name() {
        return this.sourceConfig.getSourceConnectorConfig().getConnectorName();
    }

    @Override
    public void stop() throws Exception {

    }

    @Override
    public List<ConnectRecord> poll() {
        List<ConnectRecord> connectRecords = new ArrayList<>(DEFAULT_BATCH_SIZE);

        for (int count = 0; count < DEFAULT_BATCH_SIZE; ++count) {
            try {
                ConnectRecord connectRecord = queue.poll(3, TimeUnit.SECONDS);
                if (connectRecord == null) {
                    break;
                }
                connectRecords.add(connectRecord);
            } catch (InterruptedException e) {
                Thread currentThread = Thread.currentThread();
                log.warn("[SpringSourceConnector] Interrupting thread {} due to exception {}",
                    currentThread.getName(), e.getMessage());
                currentThread.interrupt();
            }
        }
        return connectRecords;
    }

    @Override
    public void send(Object message) {
        RecordPartition partition = new RecordPartition();
        RecordOffset offset = new RecordOffset();
        ConnectRecord record = new ConnectRecord(partition, offset, System.currentTimeMillis(), message);
        queue.offer(record);
    }
}
