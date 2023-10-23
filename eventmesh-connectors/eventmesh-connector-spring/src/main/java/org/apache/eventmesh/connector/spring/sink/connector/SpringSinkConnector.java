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

package org.apache.eventmesh.connector.spring.sink.connector;

import org.apache.eventmesh.connector.spring.sink.config.SpringSinkConfig;
import org.apache.eventmesh.openconnect.api.config.Config;
import org.apache.eventmesh.openconnect.api.connector.ConnectorContext;
import org.apache.eventmesh.openconnect.api.connector.SinkConnectorContext;
import org.apache.eventmesh.openconnect.api.sink.Sink;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import lombok.extern.slf4j.Slf4j;

/**
 * Spring sink connector
 */
@Slf4j
public class SpringSinkConnector implements Sink {

    private SpringSinkConfig sinkConfig;

    private BlockingQueue<ConnectRecord> queue;

    private volatile boolean isRunning = false;

    @Override
    public Class<? extends Config> configClass() {
        return SpringSinkConfig.class;
    }

    @Override
    public void init(Config config) throws Exception {
        // init config for spring sink connector
        this.sinkConfig = (SpringSinkConfig) config;
        this.queue = new LinkedBlockingQueue<>(1000);
    }

    @Override
    public void init(ConnectorContext connectorContext) throws Exception {
        // init config for openfunction source connector
        SinkConnectorContext sinkConnectorContext = (SinkConnectorContext) connectorContext;
        this.sinkConfig = (SpringSinkConfig) sinkConnectorContext.getSinkConfig();
        this.queue = new LinkedBlockingQueue<>(1000);
    }

    @Override
    public void start() throws Exception {
        isRunning = true;
    }

    @Override
    public void commit(ConnectRecord record) {

    }

    @Override
    public String name() {
        return this.sinkConfig.getSinkConnectorConfig().getConnectorName();
    }

    @Override
    public void stop() throws Exception {

    }

    public boolean isRunning() {
        return isRunning;
    }

    public BlockingQueue<ConnectRecord> getQueue() {
        return queue;
    }

    @Override
    public void put(List<ConnectRecord> sinkRecords) {
        for (ConnectRecord connectRecord : sinkRecords) {
            try {
                queue.put(connectRecord);
            } catch (InterruptedException e) {
                Thread currentThread = Thread.currentThread();
                log.warn("[SpringSinkConnector] Interrupting thread {} due to exception {}",
                    currentThread.getName(), e.getMessage());
                currentThread.interrupt();
            }
        }
    }
}
