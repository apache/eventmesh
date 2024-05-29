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
import org.apache.eventmesh.common.config.connector.offset.OffsetStorageConfig;
import org.apache.eventmesh.common.config.connector.rdb.canal.CanalSinkConfig;
import org.apache.eventmesh.common.config.connector.rdb.canal.CanalSourceConfig;
import org.apache.eventmesh.common.config.connector.rdb.canal.SinkConnectorConfig;
import org.apache.eventmesh.common.config.connector.rdb.canal.SourceConnectorConfig;
import org.apache.eventmesh.common.remote.job.SyncConsistency;
import org.apache.eventmesh.common.remote.job.SyncMode;
import org.apache.eventmesh.common.remote.offset.RecordPosition;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.connector.canal.CanalConnectRecord;
import org.apache.eventmesh.connector.canal.DatabaseConnection;
import org.apache.eventmesh.connector.canal.source.EntryParser;
import org.apache.eventmesh.openconnect.api.ConnectorCreateService;
import org.apache.eventmesh.openconnect.api.connector.ConnectorContext;
import org.apache.eventmesh.openconnect.api.connector.SourceConnectorContext;
import org.apache.eventmesh.openconnect.api.source.Source;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;
import org.apache.eventmesh.common.remote.offset.canal.CanalRecordOffset;
import org.apache.eventmesh.common.remote.offset.canal.CanalRecordPartition;
import org.apache.eventmesh.openconnect.offsetmgmt.api.storage.OffsetStorageReader;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    private String filter = null;

    private volatile boolean running = false;

    private static final int maxEmptyTimes = 10;

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
//        this.offsetStorageReader = sourceConnectorContext.getOffsetStorageReader();
        // init source database connection
//        DatabaseConnection.sourceConfig = sourceConfig;
//        DatabaseConnection.initSourceConnection();

        canalServer = CanalServerWithEmbedded.instance();

        canalServer.setCanalInstanceGenerator(new CanalInstanceGenerator() {
            @Override
            public CanalInstance generate(String destination) {
                Canal canal = buildCanal(sourceConfig);

                CanalInstanceWithManager instance = new CanalInstanceWithManager(canal, filter) {

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
    }

    private Canal buildCanal(CanalSourceConfig sourceConfig) {
        // 设置下slaveId，保证多个piplineId下重复引用时不重复
        long slaveId = 10000;// 默认基数
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
        canalServer.start();

        canalServer.start(sourceConfig.getDestination());
        this.clientIdentity = new ClientIdentity(sourceConfig.getDestination(), sourceConfig.getClientId(), filter);
        canalServer.subscribe(clientIdentity);

        running = true;
    }


    @Override
    public void commit(ConnectRecord record) {
        long batchId = Long.parseLong(record.getExtension("messageId"));
        canalServer.ack(clientIdentity, batchId);
    }

    @Override
    public String name() {
        return this.sourceConfig.getSourceConnectorConfig().getConnectorName();
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
        if (sourceConfig.getBatchTimeout() < 0) {// perform polling
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

        EntryParser entryParser = new EntryParser();

        List<ConnectRecord> result = new ArrayList<>();

        List<CanalConnectRecord> connectRecordList = entryParser.parse(sourceConfig, entries);

        if (connectRecordList != null && !connectRecordList.isEmpty()) {
            CanalConnectRecord lastRecord = connectRecordList.get(connectRecordList.size() - 1);

            CanalRecordPartition canalRecordPartition = new CanalRecordPartition();
            canalRecordPartition.setJournalName(lastRecord.getJournalName());
            canalRecordPartition.setTimeStamp(lastRecord.getExecuteTime());

            CanalRecordOffset canalRecordOffset = new CanalRecordOffset();
            canalRecordOffset.setOffset(lastRecord.getBinLogOffset());

            ConnectRecord connectRecord = new ConnectRecord(canalRecordPartition, canalRecordOffset, System.currentTimeMillis());
            connectRecord.addExtension("messageId", String.valueOf(message.getId()));
            connectRecord.setData(connectRecordList);
            result.add(connectRecord);
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

    public static void main(String[] args) {
        CanalSourceConfig canalSourceConfig = new CanalSourceConfig();
        canalSourceConfig.setCanalInstanceId(12L);
        canalSourceConfig.setDesc("canalSourceDemo");
        canalSourceConfig.setSlaveId(123L);
        canalSourceConfig.setClientId((short) 1);
        canalSourceConfig.setDestination("destinationGroup");
        canalSourceConfig.setDdlSync(false);
        canalSourceConfig.setFilterTableError(false);
        canalSourceConfig.setSyncMode(SyncMode.ROW);
        canalSourceConfig.setSyncConsistency(SyncConsistency.BASE);

        SourceConnectorConfig sourceConnectorConfig = new SourceConnectorConfig();
        sourceConnectorConfig.setConnectorName("canalSourceConnector");
        sourceConnectorConfig.setDbAddress("127.0.0.1");
        sourceConnectorConfig.setDbPort(3306);
        sourceConnectorConfig.setUrl("jdbc:mysql://127.0.0.1:3306/test_db?serverTimezone=GMT%2B8&characterEncoding=utf-8&useSSL=false");
        sourceConnectorConfig.setSchemaName("test_db");
        sourceConnectorConfig.setTableName("people");
        sourceConnectorConfig.setUserName("root");
        sourceConnectorConfig.setPassWord("mike920830");

        OffsetStorageConfig offsetStorageConfig = new OffsetStorageConfig();
        offsetStorageConfig.setOffsetStorageAddr("127.0.0.1:8081");
        offsetStorageConfig.setOffsetStorageType("admin");
        Map<String, String> extensionMap = new HashMap<>();
        extensionMap.put("jobId", "1");
        offsetStorageConfig.setExtensions(extensionMap);

        canalSourceConfig.setSourceConnectorConfig(sourceConnectorConfig);
        canalSourceConfig.setOffsetStorageConfig(offsetStorageConfig);

        System.out.println(JsonUtils.toJSONString(canalSourceConfig));

        CanalSinkConfig canalSinkConfig = new CanalSinkConfig();
        canalSinkConfig.setSyncMode(SyncMode.ROW);

        SinkConnectorConfig sinkConnectorConfig = new SinkConnectorConfig();
        sinkConnectorConfig.setConnectorName("canalSinkConnector");
        sinkConnectorConfig.setDbAddress("127.0.0.1");
        sinkConnectorConfig.setDbPort(25000);
        sinkConnectorConfig.setUrl("jdbc:mysql://127.0.0.1:25000/test_db?serverTimezone=GMT%2B8&characterEncoding=utf-8&useSSL=false");
        sinkConnectorConfig.setSchemaName("test_db");
        sinkConnectorConfig.setTableName("people");
        sinkConnectorConfig.setUserName("clougence");
        sinkConnectorConfig.setPassWord("123456");

        canalSinkConfig.setSinkConnectorConfig(sinkConnectorConfig);

        System.out.println(JsonUtils.toJSONString(canalSinkConfig));
    }
}
