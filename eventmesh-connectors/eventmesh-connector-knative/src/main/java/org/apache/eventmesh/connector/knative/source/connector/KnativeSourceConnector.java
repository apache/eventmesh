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

package org.apache.eventmesh.connector.knative.source.connector;

import org.apache.eventmesh.common.ThreadPoolFactory;
import org.apache.eventmesh.connector.knative.source.config.KnativeSourceConfig;
import org.apache.eventmesh.openconnect.api.config.Config;
import org.apache.eventmesh.openconnect.api.connector.ConnectorContext;
import org.apache.eventmesh.openconnect.api.connector.SourceConnectorContext;
import org.apache.eventmesh.openconnect.api.source.Source;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;
import org.apache.eventmesh.openconnect.util.CloudEventUtil;

import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.ListenableFuture;
import org.asynchttpclient.Response;
import org.asynchttpclient.util.HttpConstants;

import io.cloudevents.CloudEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import lombok.extern.slf4j.Slf4j;

import static org.asynchttpclient.Dsl.asyncHttpClient;

@Slf4j
public class KnativeSourceConnector implements Source {

    private KnativeSourceConfig sourceConfig;

    private static final AtomicBoolean started = new AtomicBoolean(false);

    private static final int DEFAULT_BATCH_SIZE = 10;

    private BlockingQueue<CloudEvent> queue;

    private final ThreadPoolExecutor executor = ThreadPoolFactory.createThreadPoolExecutor(
            Runtime.getRuntime().availableProcessors() * 2,
            Runtime.getRuntime().availableProcessors() * 2,
            "EventMesh-RabbitMQSourceConnector-");

    @Override
    public Class<? extends Config> configClass() {
        return KnativeSourceConfig.class;
    }

    @Override
    public void init(Config config) throws Exception {
    }

    @Override
    public void init(ConnectorContext connectorContext) throws Exception {
        SourceConnectorContext sourceConnectorContext = (SourceConnectorContext) connectorContext;
        this.sourceConfig = (KnativeSourceConfig) sourceConnectorContext.getSourceConfig();
        this.queue = new LinkedBlockingQueue<>(1000);
    }

    @Override
    public void start() throws Exception {
        started.compareAndSet(false, true);
        executor.execute(new KnativeSourceHandler(this.sourceConfig));
    }

    @Override
    public void commit(ConnectRecord record) {
    }

    @Override
    public String name() {
        return this.sourceConfig.getConnectorConfig().getConnectorName();
    }

    @Override
    public void stop() {
        started.compareAndSet(true, false);
    }

    @Override
    public List<ConnectRecord> poll() {
        List<ConnectRecord> connectRecords = new ArrayList<>(DEFAULT_BATCH_SIZE);
        for (int count = 0; count < DEFAULT_BATCH_SIZE; ++count) {
            try {
                CloudEvent event = queue.poll(3, TimeUnit.SECONDS);
                if (event == null) {
                    break;
                }

                connectRecords.add(CloudEventUtil.convertEventToRecord(event));
            } catch (InterruptedException e) {
                break;
            }
        }
        return connectRecords;
    }

    public class KnativeSourceHandler implements Runnable {

        private final KnativeSourceConfig sourceConfig;

        private final AtomicBoolean running = new AtomicBoolean(true);

        private final transient AsyncHttpClient asyncHttpClient = asyncHttpClient();

        public KnativeSourceHandler(KnativeSourceConfig sourceConfig) {
            this.sourceConfig = sourceConfig;
        }

        @Override
        public void run() {
            while (running.get()) {
                try {
                    ListenableFuture<Response> execute = asyncHttpClient.prepareGet("http://" + sourceConfig.getConnectorConfig().getServiceAddr() + "/" + "*").execute();
                    Response response = execute.get(10, TimeUnit.SECONDS);

                    if (response.getStatusCode() != HttpConstants.ResponseStatusCodes.OK_200) {
                        log.error("HTTP response code error: " + response.getStatusCode());
                    }
                    String responseBody = response.getResponseBody();

                } catch (Exception ex) {
                    log.error("[KnativeSourceHandler] thread run happen exception.", ex);
                }
            }
        }

        public void stop() {
            running.compareAndSet(true, false);
        }
    }
}
