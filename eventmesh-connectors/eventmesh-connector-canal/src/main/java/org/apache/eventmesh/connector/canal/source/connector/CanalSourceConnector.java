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

import org.apache.eventmesh.common.config.connector.Config;
import org.apache.eventmesh.common.config.connector.rdb.canal.CanalSourceConfig;
import org.apache.eventmesh.common.remote.offset.RecordPosition;
import org.apache.eventmesh.common.remote.offset.canal.CanalRecordOffset;
import org.apache.eventmesh.common.remote.offset.canal.CanalRecordPartition;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.connector.canal.CanalConnectRecord;
import org.apache.eventmesh.connector.canal.DatabaseConnection;
import org.apache.eventmesh.connector.canal.source.EntryParser;
import org.apache.eventmesh.connector.canal.source.table.RdbTableMgr;
import org.apache.eventmesh.openconnect.api.ConnectorCreateService;
import org.apache.eventmesh.openconnect.api.connector.ConnectorContext;
import org.apache.eventmesh.openconnect.api.connector.SourceConnectorContext;
import org.apache.eventmesh.openconnect.api.source.Source;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;

import org.apache.commons.lang3.StringUtils;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import com.alibaba.otter.canal.instance.core.CanalInstance;
import com.alibaba.otter.canal.instance.core.CanalInstanceGenerator;
import com.alibaba.otter.canal.instance.manager.CanalInstanceWithManager;
import com.alibaba.otter.canal.instance.manager.model.Canal;
import com.alibaba.otter.canal.instance.manager.model.CanalParameter;
import com.alibaba.otter.canal.instance.manager.model.CanalParameter.ClusterMode;
import com.alibaba.otter.canal.instance.manager.model.CanalParameter.HAMode;
import com.alibaba.otter.canal.instance.manager.model.CanalParameter.IndexMode;
import com.alibaba.otter.canal.instance.manager.model.CanalParameter.MetaMode;
import com.alibaba.otter.canal.instance.manager.model.CanalParameter.RunMode;
import com.alibaba.otter.canal.instance.manager.model.CanalParameter.SourcingType;
import com.alibaba.otter.canal.instance.manager.model.CanalParameter.StorageMode;
import com.alibaba.otter.canal.parse.CanalEventParser;
import com.alibaba.otter.canal.parse.ha.CanalHAController;
import com.alibaba.otter.canal.parse.inbound.mysql.MysqlEventParser;
import com.alibaba.otter.canal.protocol.CanalEntry;
import com.alibaba.otter.canal.protocol.CanalEntry.Entry;
import com.alibaba.otter.canal.protocol.ClientIdentity;
import com.alibaba.otter.canal.server.embedded.CanalServerWithEmbedded;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CanalSourceConnector implements Source, ConnectorCreateService<Source> {

    private CanalSourceConfig sourceConfig;

    private CanalServerWithEmbedded canalServer;

    private ClientIdentity clientIdentity;

    private String tableFilter = null;

    private String fieldFilter = null;

    private volatile boolean running = false;

    private static final int maxEmptyTimes = 10;

    private RdbTableMgr tableMgr;

    @Override
    public Class<? extends Config> configClass() {
        return CanalSourceConfig.class;
    }

    @Override
    public void init(Config config) throws Exception {
        // init config for canal source connector
        this.sourceConfig = (CanalSourceConfig) config;
    }

    @Override
    public void init(ConnectorContext connectorContext) throws Exception {
        SourceConnectorContext sourceConnectorContext = (SourceConnectorContext) connectorContext;
        this.sourceConfig = (CanalSourceConfig) sourceConnectorContext.getSourceConfig();
        if (sourceConnectorContext.getRecordPositionList() != null) {
            this.sourceConfig.setRecordPositions(sourceConnectorContext.getRecordPositionList());
        }

        if (StringUtils.isNotEmpty(sourceConfig.getTableFilter())) {
            tableFilter = sourceConfig.getTableFilter();
        }
        if (StringUtils.isNotEmpty(sourceConfig.getFieldFilter())) {
            fieldFilter = sourceConfig.getFieldFilter();
        }

        canalServer = CanalServerWithEmbedded.instance();

        canalServer.setCanalInstanceGenerator(new CanalInstanceGenerator() {
            @Override
            public CanalInstance generate(String destination) {
                Canal canal = buildCanal(sourceConfig);

                CanalInstanceWithManager instance = new CanalInstanceWithManager(canal, tableFilter) {

                    protected CanalHAController initHaController() {
                        return super.initHaController();
                    }

                    protected void startEventParserInternal(CanalEventParser parser, boolean isGroup) {
                        super.startEventParserInternal(parser, isGroup);

                        if (eventParser instanceof MysqlEventParser) {
                            // set eventParser support type
                            ((MysqlEventParser) eventParser).setSupportBinlogFormats("ROW");
                            ((MysqlEventParser) eventParser).setSupportBinlogImages("FULL");
                            MysqlEventParser mysqlEventParser = (MysqlEventParser) eventParser;
                            mysqlEventParser.setParallel(false);
                            if (StringUtils.isNotEmpty(fieldFilter)) {
                                mysqlEventParser.setFieldFilter(fieldFilter);
                            }

                            CanalHAController haController = mysqlEventParser.getHaController();
                            if (!haController.isStart()) {
                                haController.start();
                            }
                        }
                    }
                };
                return instance;
            }
        });
        DatabaseConnection.sourceConfig = sourceConfig.getSourceConnectorConfig();
        DatabaseConnection.initSourceConnection();
        tableMgr = new RdbTableMgr(sourceConfig.getSourceConnectorConfig(), DatabaseConnection.sourceDataSource);
    }

    private Canal buildCanal(CanalSourceConfig sourceConfig) {
        long slaveId = 10000;
        if (sourceConfig.getSlaveId() != null) {
            slaveId = sourceConfig.getSlaveId();
        }

        Canal canal = new Canal();
        canal.setId(sourceConfig.getCanalInstanceId());
        canal.setName(sourceConfig.getDestination());
        canal.setDesc(sourceConfig.getDesc());

        CanalParameter parameter = new CanalParameter();

        parameter.setRunMode(RunMode.EMBEDDED);
        parameter.setClusterMode(ClusterMode.STANDALONE);
        parameter.setMetaMode(MetaMode.MEMORY);
        parameter.setHaMode(HAMode.HEARTBEAT);
        parameter.setIndexMode(IndexMode.MEMORY);
        parameter.setStorageMode(StorageMode.MEMORY);
        parameter.setMemoryStorageBufferSize(32 * 1024);

        parameter.setSourcingType(SourcingType.MYSQL);
        parameter.setDbAddresses(Collections.singletonList(new InetSocketAddress(sourceConfig.getSourceConnectorConfig().getDbAddress(),
            sourceConfig.getSourceConnectorConfig().getDbPort())));
        parameter.setDbUsername(sourceConfig.getSourceConnectorConfig().getUserName());
        parameter.setDbPassword(sourceConfig.getSourceConnectorConfig().getPassWord());

        // set if enabled gtid mode
        parameter.setGtidEnable(sourceConfig.isGTIDMode());

        // check positions
        // example: Arrays.asList("{\"journalName\":\"mysql-bin.000001\",\"position\":6163L,\"timestamp\":1322803601000L}",
        //         "{\"journalName\":\"mysql-bin.000001\",\"position\":6163L,\"timestamp\":1322803601000L}")
        if (sourceConfig.getRecordPositions() != null && !sourceConfig.getRecordPositions().isEmpty()) {
            List<RecordPosition> recordPositions = sourceConfig.getRecordPositions();
            List<String> positions = new ArrayList<>();
            recordPositions.forEach(recordPosition -> {
                Map<String, Object> recordPositionMap = new HashMap<>();
                CanalRecordPartition canalRecordPartition = (CanalRecordPartition) (recordPosition.getRecordPartition());
                CanalRecordOffset canalRecordOffset = (CanalRecordOffset) (recordPosition.getRecordOffset());
                recordPositionMap.put("journalName", canalRecordPartition.getJournalName());
                recordPositionMap.put("timestamp", canalRecordPartition.getTimeStamp());
                recordPositionMap.put("position", canalRecordOffset.getOffset());
                // for mariaDB not support gtid mode
                if (sourceConfig.isGTIDMode() && !sourceConfig.isMariaDB()) {
                    String gtidRange = canalRecordOffset.getGtid();
                    if (gtidRange != null) {
                        if (canalRecordOffset.getCurrentGtid() != null) {
                            gtidRange = EntryParser.replaceGtidRange(canalRecordOffset.getGtid(), canalRecordOffset.getCurrentGtid(),
                                sourceConfig.getServerUUID());
                        }
                        recordPositionMap.put("gtid", gtidRange);
                    }
                }
                positions.add(JsonUtils.toJSONString(recordPositionMap));
            });
            parameter.setPositions(positions);
        }

        parameter.setSlaveId(slaveId);

        parameter.setDefaultConnectionTimeoutInSeconds(30);
        parameter.setConnectionCharset("UTF-8");
        parameter.setConnectionCharsetNumber((byte) 33);
        parameter.setReceiveBufferSize(8 * 1024);
        parameter.setSendBufferSize(8 * 1024);

        // heartbeat detect
        parameter.setDetectingEnable(false);

        parameter.setDdlIsolation(sourceConfig.isDdlSync());
        parameter.setFilterTableError(sourceConfig.isFilterTableError());
        parameter.setMemoryStorageRawEntry(false);

        canal.setCanalParameter(parameter);
        return canal;
    }


    @Override
    public void start() throws Exception {
        if (running) {
            return;
        }
        tableMgr.start();
        canalServer.start();

        canalServer.start(sourceConfig.getDestination());
        this.clientIdentity = new ClientIdentity(sourceConfig.getDestination(), sourceConfig.getClientId(), tableFilter);
        canalServer.subscribe(clientIdentity);

        running = true;
    }


    @Override
    public void commit(ConnectRecord record) {
        long batchId = Long.parseLong(record.getExtension("messageId"));
        int batchIndex = record.getExtension("batchIndex", Integer.class);
        int totalBatches = record.getExtension("totalBatches", Integer.class);
        if (batchIndex == totalBatches - 1) {
            log.debug("ack records batchIndex:{}, totalBatches:{}, batchId:{}",
                batchIndex, totalBatches, batchId);
            canalServer.ack(clientIdentity, batchId);
        }
    }

    @Override
    public String name() {
        return this.sourceConfig.getSourceConnectorConfig().getConnectorName();
    }

    @Override
    public void onException(ConnectRecord record) {

    }

    @Override
    public void stop() {
        if (!running) {
            return;
        }
        running = false;
        canalServer.stop(sourceConfig.getDestination());
        canalServer.stop();
    }

    @Override
    public List<ConnectRecord> poll() {
        int emptyTimes = 0;
        com.alibaba.otter.canal.protocol.Message message = null;
        if (sourceConfig.getBatchTimeout() < 0) {
            while (running) {
                message = canalServer.getWithoutAck(clientIdentity, sourceConfig.getBatchSize());
                if (message == null || message.getId() == -1L) { // empty
                    applyWait(emptyTimes++);
                } else {
                    break;
                }
            }
        } else { // perform with timeout
            while (running) {
                message =
                    canalServer.getWithoutAck(clientIdentity, sourceConfig.getBatchSize(), sourceConfig.getBatchTimeout(), TimeUnit.MILLISECONDS);
                if (message == null || message.getId() == -1L) { // empty
                    continue;
                }
                break;
            }
        }

        List<Entry> entries;
        assert message != null;
        if (message.isRaw()) {
            entries = new ArrayList<>(message.getRawEntries().size());
            for (ByteString entry : message.getRawEntries()) {
                try {
                    entries.add(CanalEntry.Entry.parseFrom(entry));
                } catch (InvalidProtocolBufferException e) {
                    throw new RuntimeException(e);
                }
            }
        } else {
            entries = message.getEntries();
        }

        List<ConnectRecord> result = new ArrayList<>();
        // key: Xid offset
        Map<Long, List<CanalConnectRecord>> connectorRecordMap = EntryParser.parse(sourceConfig, entries, tableMgr);

        if (!connectorRecordMap.isEmpty()) {
            Set<Map.Entry<Long, List<CanalConnectRecord>>> entrySet = connectorRecordMap.entrySet();
            for (Map.Entry<Long, List<CanalConnectRecord>> entry : entrySet) {
                List<CanalConnectRecord> connectRecordList = entry.getValue();
                CanalConnectRecord lastRecord = entry.getValue().get(connectRecordList.size() - 1);
                CanalRecordPartition canalRecordPartition = new CanalRecordPartition();
                canalRecordPartition.setServerUUID(sourceConfig.getServerUUID());
                canalRecordPartition.setJournalName(lastRecord.getJournalName());
                canalRecordPartition.setTimeStamp(lastRecord.getExecuteTime());
                // Xid offset with gtid
                Long binLogOffset = entry.getKey();
                CanalRecordOffset canalRecordOffset = new CanalRecordOffset();
                canalRecordOffset.setOffset(binLogOffset);
                if (StringUtils.isNotEmpty(lastRecord.getGtid()) && StringUtils.isNotEmpty(lastRecord.getCurrentGtid())) {
                    canalRecordOffset.setGtid(lastRecord.getGtid());
                    canalRecordOffset.setCurrentGtid(lastRecord.getCurrentGtid());
                }

                // split record list
                List<List<CanalConnectRecord>> splitLists = new ArrayList<>();
                for (int i = 0; i < connectRecordList.size(); i += sourceConfig.getBatchSize()) {
                    int end = Math.min(i + sourceConfig.getBatchSize(), connectRecordList.size());
                    List<CanalConnectRecord> subList = connectRecordList.subList(i, end);
                    splitLists.add(subList);
                }

                for (int i = 0; i < splitLists.size(); i++) {
                    ConnectRecord connectRecord = new ConnectRecord(canalRecordPartition, canalRecordOffset, System.currentTimeMillis());
                    connectRecord.addExtension("messageId", String.valueOf(message.getId()));
                    connectRecord.addExtension("batchIndex", i);
                    connectRecord.addExtension("totalBatches", splitLists.size());
                    connectRecord.setData(splitLists.get(i));
                    result.add(connectRecord);
                }
            }
        } else {
            // for the message has been filtered need ack message
            canalServer.ack(clientIdentity, message.getId());
        }

        return result;
    }

    // Handle the situation of no data and avoid empty loop death
    private void applyWait(int emptyTimes) {
        int newEmptyTimes = Math.min(emptyTimes, maxEmptyTimes);
        if (emptyTimes <= 3) {
            Thread.yield();
        } else {
            LockSupport.parkNanos(1000 * 1000L * newEmptyTimes);
        }
    }

    @Override
    public Source create() {
        return new CanalSourceConnector();
    }
}
