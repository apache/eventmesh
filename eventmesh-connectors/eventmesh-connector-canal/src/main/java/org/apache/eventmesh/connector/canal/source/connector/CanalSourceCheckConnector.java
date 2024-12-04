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

package org.apache.eventmesh.connector.canal.source.connector;

import org.apache.eventmesh.common.AbstractComponent;
import org.apache.eventmesh.common.EventMeshThreadFactory;
import org.apache.eventmesh.common.config.connector.Config;
import org.apache.eventmesh.common.config.connector.rdb.canal.CanalSourceFullConfig;
import org.apache.eventmesh.common.config.connector.rdb.canal.JobRdbFullPosition;
import org.apache.eventmesh.common.config.connector.rdb.canal.RdbDBDefinition;
import org.apache.eventmesh.common.config.connector.rdb.canal.RdbTableDefinition;
import org.apache.eventmesh.common.config.connector.rdb.canal.mysql.MySQLTableDef;
import org.apache.eventmesh.common.exception.EventMeshException;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.connector.canal.DatabaseConnection;
import org.apache.eventmesh.connector.canal.source.position.CanalFullPositionMgr;
import org.apache.eventmesh.connector.canal.source.position.TableFullPosition;
import org.apache.eventmesh.connector.canal.source.table.RdbSimpleTable;
import org.apache.eventmesh.connector.canal.source.table.RdbTableMgr;
import org.apache.eventmesh.openconnect.api.ConnectorCreateService;
import org.apache.eventmesh.openconnect.api.connector.ConnectorContext;
import org.apache.eventmesh.openconnect.api.connector.SourceConnectorContext;
import org.apache.eventmesh.openconnect.api.source.Source;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CanalSourceCheckConnector extends AbstractComponent implements Source, ConnectorCreateService<Source> {

    private CanalSourceFullConfig config;
    private CanalFullPositionMgr positionMgr;
    private RdbTableMgr tableMgr;
    private ThreadPoolExecutor executor;
    private BlockingQueue<List<ConnectRecord>> queue;
    private final AtomicBoolean flag = new AtomicBoolean(true);
    private long maxPollWaitTime;

    @Override
    protected void run() throws Exception {
        this.tableMgr.start();
        this.positionMgr.start();
        if (positionMgr.isFinished()) {
            log.info("connector [{}] has finished the job", config.getSourceConnectorConfig().getConnectorName());
            return;
        }
        executor = new ThreadPoolExecutor(config.getParallel(), config.getParallel(), 0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(), new EventMeshThreadFactory("canal-source-full"));
        List<CanalFullProducer> producers = new LinkedList<>();
        if (config.getSourceConnectorConfig().getDatabases() != null) {
            for (RdbDBDefinition db : config.getSourceConnectorConfig().getDatabases()) {
                for (RdbTableDefinition table : db.getTables()) {
                    try {
                        log.info("it will create producer of db [{}] table [{}]", db.getSchemaName(), table.getTableName());
                        RdbSimpleTable simpleTable = new RdbSimpleTable(db.getSchemaName(), table.getTableName());
                        JobRdbFullPosition position = positionMgr.getPosition(simpleTable);
                        if (position == null) {
                            throw new EventMeshException(String.format("db [%s] table [%s] have none position info",
                                db.getSchemaName(), table.getTableName()));
                        }
                        RdbTableDefinition tableDefinition = tableMgr.getTable(simpleTable);
                        if (tableDefinition == null) {
                            throw new EventMeshException(String.format("db [%s] table [%s] have none table definition info",
                                db.getSchemaName(), table.getTableName()));
                        }

                        producers.add(new CanalFullProducer(queue, DatabaseConnection.sourceDataSource, (MySQLTableDef) tableDefinition,
                            JsonUtils.parseObject(position.getPrimaryKeyRecords(), TableFullPosition.class),
                            config.getFlushSize()));
                    } catch (Exception e) {
                        log.error("create schema [{}] table [{}] producers fail", db.getSchemaName(),
                            table.getTableName(), e);
                    }
                }
            }
        }
        producers.forEach(p -> executor.execute(() -> p.start(flag)));
    }

    @Override
    protected void shutdown() throws Exception {
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
        if (DatabaseConnection.sourceDataSource != null) {
            DatabaseConnection.sourceDataSource.close();
            log.info("data source has been closed");
        }
    }

    @Override
    public Source create() {
        return new CanalSourceCheckConnector();
    }

    @Override
    public Class<? extends Config> configClass() {
        return CanalSourceFullConfig.class;
    }

    @Override
    public void init(Config config) throws Exception {
        this.config = (CanalSourceFullConfig) config;
        init();
    }

    private void init() {
        DatabaseConnection.sourceConfig = this.config.getSourceConnectorConfig();
        DatabaseConnection.initSourceConnection();
        this.tableMgr = new RdbTableMgr(config.getSourceConnectorConfig(), DatabaseConnection.sourceDataSource);
        this.positionMgr = new CanalFullPositionMgr(config, tableMgr);
        this.maxPollWaitTime = config.getPollConfig().getMaxWaitTime();
        this.queue = new LinkedBlockingQueue<>(config.getPollConfig().getCapacity());
    }

    @Override
    public void init(ConnectorContext connectorContext) throws Exception {
        SourceConnectorContext sourceConnectorContext = (SourceConnectorContext) connectorContext;
        this.config = (CanalSourceFullConfig) sourceConnectorContext.getSourceConfig();
        init();
    }

    @Override
    public void commit(ConnectRecord record) {
        // nothing
    }

    @Override
    public String name() {
        return this.config.getSourceConnectorConfig().getConnectorName();
    }

    @Override
    public void onException(ConnectRecord record) {

    }

    @Override
    public List<ConnectRecord> poll() {
        while (flag.get()) {
            try {
                List<ConnectRecord> records = queue.poll(maxPollWaitTime, TimeUnit.MILLISECONDS);
                if (records == null || records.isEmpty()) {
                    continue;
                }
                return records;
            } catch (InterruptedException ignore) {
                Thread.currentThread().interrupt();
                log.info("[{}] thread interrupted", this.getClass());
                return null;
            }
        }
        log.info("[{}] life flag is stop, so return null", this.getClass());
        return null;
    }

}
