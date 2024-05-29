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

import org.apache.eventmesh.common.config.connector.Config;
import org.apache.eventmesh.common.config.connector.spring.SpringSourceConfig;
import org.apache.eventmesh.common.remote.offset.RecordOffset;
import org.apache.eventmesh.common.remote.offset.RecordPartition;
import org.apache.eventmesh.connector.spring.source.MessageSendingOperations;
import org.apache.eventmesh.openconnect.SourceWorker;
import org.apache.eventmesh.openconnect.api.callback.SendMessageCallback;
import org.apache.eventmesh.openconnect.api.connector.ConnectorContext;
import org.apache.eventmesh.openconnect.api.connector.SourceConnectorContext;
import org.apache.eventmesh.openconnect.api.source.Source;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.BeansException;
import org.springframework.boot.env.OriginTrackedMapPropertySource;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SpringSourceConnector implements Source, MessageSendingOperations, ApplicationContextAware {

    private static final String CONNECTOR_PROPERTY_PREFIX = "eventmesh.connector.";

    private static final int DEFAULT_BATCH_SIZE = 10;

    private ApplicationContext applicationContext;

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

    /**
     * Send message.
     * @param message message to send
     */
    @Override
    public void send(Object message) {
        RecordPartition partition = new RecordPartition();
        RecordOffset offset = new RecordOffset();
        ConnectRecord record = new ConnectRecord(partition, offset, System.currentTimeMillis(), message);
        addSpringEnvironmentPropertyExtensions(record);
        queue.offer(record);
    }

    /**
     * Send message with a callback.
     * @param message message to send.
     * @param workerCallback After the user sends the message to the Connector,
     *                       the SourceWorker will fetch message and invoke.
     */
    @Override
    public void send(Object message, SendMessageCallback workerCallback) {
        RecordPartition partition = new RecordPartition();
        RecordOffset offset = new RecordOffset();
        ConnectRecord record = new ConnectRecord(partition, offset, System.currentTimeMillis(), message);
        record.addExtension(SourceWorker.CALLBACK_EXTENSION, workerCallback);
        addSpringEnvironmentPropertyExtensions(record);
        queue.offer(record);
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    private void addSpringEnvironmentPropertyExtensions(ConnectRecord connectRecord) {
        ConfigurableApplicationContext context = (ConfigurableApplicationContext) applicationContext;
        MutablePropertySources propertySources = context.getEnvironment().getPropertySources();
        for (PropertySource<?> propertySource : propertySources) {
            if (!(propertySource instanceof OriginTrackedMapPropertySource)) {
                continue;
            }
            OriginTrackedMapPropertySource originTrackedMapPropertySource =
                (OriginTrackedMapPropertySource) propertySource;
            String[] keys = originTrackedMapPropertySource.getPropertyNames();
            for (String key : keys) {
                if (!key.startsWith(CONNECTOR_PROPERTY_PREFIX)) {
                    continue;
                }
                Object value = null;
                try {
                    value = originTrackedMapPropertySource.getProperty(key);
                    if (value != null) {
                        connectRecord.addExtension(key.replaceAll(CONNECTOR_PROPERTY_PREFIX, "").toLowerCase(),
                            String.valueOf(value));
                    }
                } catch (Throwable e) {
                    log.error("Put spring environment property to extension failed, key=[{}], value=[{}]", key, value);
                }
            }
        }
    }
}
