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

package org.apache.eventmesh.connector.canal.sink.connector;

import org.apache.eventmesh.common.EventMeshThreadFactory;
import org.apache.eventmesh.common.config.connector.Config;
import org.apache.eventmesh.common.config.connector.rdb.canal.CanalSinkConfig;
import org.apache.eventmesh.common.config.connector.rdb.canal.CanalSinkFullConfig;
import org.apache.eventmesh.common.exception.EventMeshException;
import org.apache.eventmesh.connector.canal.DatabaseConnection;
import org.apache.eventmesh.connector.canal.source.table.RdbTableMgr;
import org.apache.eventmesh.openconnect.api.ConnectorCreateService;
import org.apache.eventmesh.openconnect.api.connector.ConnectorContext;
import org.apache.eventmesh.openconnect.api.connector.SinkConnectorContext;
import org.apache.eventmesh.openconnect.api.sink.Sink;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;
import org.apache.eventmesh.openconnect.util.ConfigUtil;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CanalSinkFullConnector implements Sink, ConnectorCreateService<Sink> {

    private CanalSinkFullConfig config;
    private RdbTableMgr tableMgr;
    private ThreadPoolExecutor executor;
    private final BlockingQueue<List<ConnectRecord>> queue = new LinkedBlockingQueue<>(10000);
    private final AtomicBoolean flag = new AtomicBoolean(true);

    @Override
    public void start() throws Exception {
        tableMgr.start();
    }

    @Override
    public void stop() throws Exception {
        flag.set(false);
        if (!executor.isShutdown()) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    log.warn("wait thread pool shutdown timeout, it will shutdown now");
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.info("shutdown thread pool fail");
            }
        }
        if (DatabaseConnection.sinkDataSource != null) {
            DatabaseConnection.sinkDataSource.close();
            log.info("data source has been closed");
        }
    }

    @Override
    public Sink create() {
        return new CanalSinkFullConnector();
    }

    @Override
    public Class<? extends Config> configClass() {
        return CanalSinkFullConfig.class;
    }

    @Override
    public void init(Config config) throws Exception {
        this.config = (CanalSinkFullConfig) config;
        init();
    }

    @Override
    public void init(ConnectorContext connectorContext) throws Exception {
        SinkConnectorContext sinkConnectorContext = (SinkConnectorContext) connectorContext;
        CanalSinkConfig canalSinkConfig = (CanalSinkConfig) sinkConnectorContext.getSinkConfig();
        this.config = ConfigUtil.parse(canalSinkConfig.getSinkConfig(), CanalSinkFullConfig.class);
        init();
    }

    private void init() {
        if (config.getSinkConnectorConfig() == null) {
            throw new EventMeshException(String.format("[%s] sink config is null", this.getClass()));
        }
        DatabaseConnection.sinkConfig = this.config.getSinkConnectorConfig();
        DatabaseConnection.initSinkConnection();
        DatabaseConnection.sinkDataSource.setDefaultAutoCommit(false);

        tableMgr = new RdbTableMgr(this.config.getSinkConnectorConfig(), DatabaseConnection.sinkDataSource);
        executor = new ThreadPoolExecutor(config.getParallel(), config.getParallel(), 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(), new EventMeshThreadFactory("canal-sink-full"));
        List<CanalFullConsumer> consumers = new LinkedList<>();
        for (int i = 0; i < config.getParallel(); i++) {
            CanalFullConsumer canalFullConsumer = new CanalFullConsumer(queue, tableMgr, config);
            consumers.add(canalFullConsumer);
        }
        consumers.forEach(c -> executor.execute(() -> c.start(flag)));
    }

    @Override
    public void commit(ConnectRecord record) {

    }

    @Override
    public String name() {
        return null;
    }

    @Override
    public void onException(ConnectRecord record) {

    }

    @Override
    public void put(List<ConnectRecord> sinkRecords) {
        if (sinkRecords == null || sinkRecords.isEmpty() || sinkRecords.get(0) == null) {
            if (log.isDebugEnabled()) {
                log.debug("[{}] got sink records are none", this.getClass());
            }
            return;
        }
        try {
            queue.put(sinkRecords);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

    }

}
