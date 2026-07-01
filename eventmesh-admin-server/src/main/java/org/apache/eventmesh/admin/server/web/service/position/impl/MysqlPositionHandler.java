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

package org.apache.eventmesh.admin.server.web.service.position.impl;

import org.apache.eventmesh.admin.server.web.db.entity.EventMeshMysqlPosition;
import org.apache.eventmesh.admin.server.web.db.entity.EventMeshPositionReporterHistory;
import org.apache.eventmesh.admin.server.web.db.service.EventMeshMysqlPositionService;
import org.apache.eventmesh.admin.server.web.db.service.EventMeshPositionReporterHistoryService;
import org.apache.eventmesh.admin.server.web.pojo.BinlogPosition;
import org.apache.eventmesh.admin.server.web.pojo.JobDetail;
import org.apache.eventmesh.admin.server.web.service.job.JobInfoBizService;
import org.apache.eventmesh.admin.server.web.service.position.PositionHandler;
import org.apache.eventmesh.admin.server.web.utils.JdbcUtils;
import org.apache.eventmesh.common.config.connector.rdb.canal.CanalSourceConfig;
import org.apache.eventmesh.common.config.connector.rdb.canal.CanalSourceFullConfig;
import org.apache.eventmesh.common.protocol.grpc.adminserver.Metadata;
import org.apache.eventmesh.common.remote.datasource.DataSourceType;
import org.apache.eventmesh.common.remote.offset.RecordPosition;
import org.apache.eventmesh.common.remote.offset.canal.CanalRecordOffset;
import org.apache.eventmesh.common.remote.offset.canal.CanalRecordPartition;
import org.apache.eventmesh.common.remote.request.FetchPositionRequest;
import org.apache.eventmesh.common.remote.request.RecordPositionRequest;
import org.apache.eventmesh.common.remote.request.ReportPositionRequest;
import org.apache.eventmesh.common.utils.JsonUtils;

import org.apache.commons.lang3.StringUtils;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.LockSupport;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import com.alibaba.druid.pool.DruidDataSource;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class MysqlPositionHandler extends PositionHandler {
    private static final int RETRY_TIMES = 3;
    private static final String SQL_SELECT_RDB_VERSION = "select version() as rdb_version";
    private static final String SQL_SHOW_BINLOG_POSITION = "SHOW MASTER STATUS";
    private static final String SQL_SELECT_SERVER_UUID_IN_MARIADB = "SELECT @@global.server_id as server_uuid";
    private static final String SQL_SHOW_SERVER_UUID_IN_MYSQL = "SELECT @@server_uuid as server_uuid";
    private static final String SQL_SELECT_GTID_IN_MARIADB = "SELECT @@global.gtid_binlog_pos as gtid";
    private static final String SQL_SELECT_GTID_IN_MYSQL = "SELECT @@gtid_executed as gtid";

    private final long retryPeriod = Duration.ofMillis(500).toNanos();

    @Autowired
    EventMeshMysqlPositionService positionService;

    @Autowired
    EventMeshPositionReporterHistoryService historyService;

    @Autowired
    JobInfoBizService jobInfoBizService;

    @Override
    protected DataSourceType getSourceType() {
        return DataSourceType.MYSQL;
    }

    private boolean isNotForward(EventMeshMysqlPosition now, EventMeshMysqlPosition old) {
        if (StringUtils.isNotBlank(old.getJournalName()) && old.getJournalName().equals(now.getJournalName())
            && old.getPosition() >= now.getPosition()) {
            log.info("job [{}] report position [{}] by runtime [{}] less than db position [{}] journal name [{}] by [{}]", now.getJobID(),
                now.getPosition(), now.getAddress(), now.getJournalName(), old.getPosition(), old.getAddress());
            return true;
        }
        return false;
    }

    public boolean saveOrUpdateByJob(EventMeshMysqlPosition position) {
        for (int i = 0; i < RETRY_TIMES; i++) {
            EventMeshMysqlPosition old = positionService.getOne(Wrappers.<EventMeshMysqlPosition>query().eq("jobId", position.getJobID()));
            if (old == null) {
                try {
                    return positionService.save(position);
                } catch (DuplicateKeyException e) {
                    log.warn("current insert position fail, it will retry in 500ms");
                    LockSupport.parkNanos(retryPeriod);
                    continue;
                } catch (Exception e) {
                    log.warn("insert position fail catch unknown exception", e);
                    return false;
                }
            }

            if (isNotForward(position, old)) {
                return true;
            }
            try {
                if (!positionService.update(position,
                    Wrappers.<EventMeshMysqlPosition>update().eq("updateTime", old.getUpdateTime()).eq("jobID", old.getJobID()))) {
                    log.warn("update position [{}] fail, maybe current update. it will retry in 500ms", position);
                    LockSupport.parkNanos(retryPeriod);
                    continue;
                }
                return true;
            } finally {
                if (old.getAddress() != null && !old.getAddress().equals(position.getAddress())) {
                    EventMeshPositionReporterHistory history = new EventMeshPositionReporterHistory();
                    history.setRecord(JsonUtils.toJSONString(position));
                    history.setJob(old.getJobID());
                    history.setAddress(old.getAddress());
                    log.info("job [{}] position reporter changed old [{}], now [{}]", position.getJobID(), old, position);
                    try {
                        historyService.save(history);
                    } catch (Exception e) {
                        log.warn("save job [{}] mysql position reporter changed history fail, now reporter [{}], old [{}]", position.getJobID(),
                            position.getAddress(), old.getAddress(), e);
                    }
                }
            }
        }
        return false;
    }

    @Override
    public boolean handler(ReportPositionRequest request, Metadata metadata) {
        try {
            List<RecordPosition> recordPositionList = request.getRecordPositionList();
            RecordPosition recordPosition = recordPositionList.get(0);
            if (recordPosition == null || recordPosition.getRecordPartition() == null || recordPosition.getRecordOffset() == null) {
                log.warn("report mysql position, but record-partition/partition/offset is null");
                return false;
            }
            if (!(recordPosition.getRecordPartition() instanceof CanalRecordPartition)) {
                log.warn("report mysql position, but record partition class [{}] not match [{}]",
                    recordPosition.getRecordPartition().getRecordPartitionClass(), CanalRecordPartition.class);
                return false;
            }
            if (!(recordPosition.getRecordOffset() instanceof CanalRecordOffset)) {
                log.warn("report mysql position, but record offset class [{}] not match [{}]",
                    recordPosition.getRecordOffset().getRecordOffsetClass(), CanalRecordOffset.class);
                return false;
            }
            EventMeshMysqlPosition position = new EventMeshMysqlPosition();
            position.setJobID(request.getJobID());
            position.setAddress(request.getAddress());
            CanalRecordOffset offset = (CanalRecordOffset) recordPosition.getRecordOffset();
            if (offset != null) {
                position.setPosition(offset.getOffset());
                position.setGtid(offset.getGtid());
                position.setCurrentGtid(offset.getCurrentGtid());
            }
            CanalRecordPartition partition = (CanalRecordPartition) recordPosition.getRecordPartition();
            if (partition != null) {
                position.setServerUUID(partition.getServerUUID());
                position.setTimestamp(partition.getTimeStamp());
                position.setJournalName(partition.getJournalName());
            }
            if (!saveOrUpdateByJob(position)) {
                log.warn("update job position fail [{}]", request);
                return false;
            }
            return true;
        } catch (Exception e) {
            log.warn("save position job [{}] fail", request.getJobID(), e);
        }

        return false;
    }

    @Override
    public List<RecordPosition> handler(FetchPositionRequest request, Metadata metadata) {
        List<EventMeshMysqlPosition> positionList = positionService.list(Wrappers.<EventMeshMysqlPosition>query().eq("jobID", request.getJobID()));
        List<RecordPosition> recordPositionList = new ArrayList<>();
        for (EventMeshMysqlPosition position : positionList) {
            CanalRecordPartition partition = new CanalRecordPartition();
            partition.setTimeStamp(position.getTimestamp());
            partition.setJournalName(position.getJournalName());
            partition.setServerUUID(position.getServerUUID());
            RecordPosition recordPosition = new RecordPosition();
            recordPosition.setRecordPartition(partition);
            CanalRecordOffset offset = new CanalRecordOffset();
            offset.setOffset(position.getPosition());
            offset.setGtid(position.getGtid());
            offset.setCurrentGtid(position.getCurrentGtid());
            recordPosition.setRecordOffset(offset);
            recordPositionList.add(recordPosition);
        }
        return recordPositionList;
    }

    @Override
    public boolean handler(RecordPositionRequest request, Metadata metadata) {
        try {
            String fullJobID = request.getFullJobID();
            String increaseJobID = request.getIncreaseJobID();
            log.info("start record full job position to increase job position,full jobID:{}, increase jobID:{}.", fullJobID, increaseJobID);
            JobDetail fullJobDetail = jobInfoBizService.getJobDetail(fullJobID);
            CanalSourceConfig canalSourceConfig = (CanalSourceConfig) fullJobDetail.getSourceDataSource().getConf();
            CanalSourceFullConfig canalSourceFullConfig = JsonUtils.mapToObject(canalSourceConfig.getSourceConfig(), CanalSourceFullConfig.class);
            try (DruidDataSource druidDataSource = JdbcUtils.createDruidDataSource(canalSourceFullConfig.getSourceConnectorConfig().getUrl(),
                canalSourceFullConfig.getSourceConnectorConfig().getUserName(), canalSourceFullConfig.getSourceConnectorConfig().getPassWord())) {

                DataSourceType dataSourceType = checkRDBDataSourceType(druidDataSource);

                ReportPositionRequest reportPositionRequest = new ReportPositionRequest();
                reportPositionRequest.setJobID(increaseJobID);
                reportPositionRequest.setDataSourceType(DataSourceType.MYSQL);
                reportPositionRequest.setAddress(request.getAddress());

                RecordPosition recordPosition = new RecordPosition();
                CanalRecordOffset recordOffset = new CanalRecordOffset();
                BinlogPosition binlogPosition = queryBinlogPosition(druidDataSource);
                String gtid = queryGTID(druidDataSource, dataSourceType);
                recordOffset.setOffset(binlogPosition.getPosition());
                recordOffset.setGtid(gtid);
                recordPosition.setRecordOffset(recordOffset);

                CanalRecordPartition recordPartition = new CanalRecordPartition();
                String serverUUID = queryServerUUID(druidDataSource, dataSourceType);
                recordPartition.setJournalName(binlogPosition.getFile());
                recordPartition.setServerUUID(serverUUID);
                recordPosition.setRecordPartition(recordPartition);

                List<RecordPosition> recordPositions = new ArrayList<>();
                recordPositions.add(recordPosition);

                reportPositionRequest.setRecordPositionList(recordPositions);
                log.info("start store increase task position,jobID:{},request:{}", increaseJobID, reportPositionRequest);
                handler(reportPositionRequest, metadata);
            }
            return true;
        } catch (Exception e) {
            log.error("record full job position to increase job position failed.", e);
            return false;
        }
    }

    private DataSourceType checkRDBDataSourceType(DruidDataSource druidDataSource) {
        try {
            log.info("execute sql '{}' start.", SQL_SELECT_RDB_VERSION);
            try (PreparedStatement preparedStatement = druidDataSource.getConnection().prepareStatement(SQL_SELECT_RDB_VERSION)) {
                ResultSet resultSet = preparedStatement.executeQuery();
                if (resultSet.next()) {
                    log.info("execute sql '{}' result:{}", SQL_SELECT_RDB_VERSION, resultSet);
                    String rdbVersion = resultSet.getString("rdb_version");
                    if (StringUtils.isNotBlank(rdbVersion)) {
                        if (rdbVersion.toLowerCase().contains(DataSourceType.MariaDB.getName().toLowerCase())) {
                            return DataSourceType.MariaDB;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("select rdb version failed,data source:{}", druidDataSource, e);
            throw new RuntimeException("select rdb version failed");
        }
        return DataSourceType.MYSQL;
    }

    private BinlogPosition queryBinlogPosition(DruidDataSource druidDataSource) {
        BinlogPosition binlogPosition = new BinlogPosition();
        try {
            log.info("execute sql '{}' start.", SQL_SHOW_BINLOG_POSITION);
            try (PreparedStatement preparedStatement = druidDataSource.getConnection().prepareStatement(SQL_SHOW_BINLOG_POSITION)) {
                ResultSet resultSet = preparedStatement.executeQuery();
                if (resultSet.next()) {
                    log.info("execute sql '{}' result:{}", SQL_SELECT_RDB_VERSION, resultSet);
                    String fileName = resultSet.getString("File");
                    Long position = resultSet.getLong("Position");
                    binlogPosition.setFile(fileName);
                    binlogPosition.setPosition(position);
                }
            }
        } catch (Exception e) {
            log.warn("show binlog position failed,data source:{}", druidDataSource, e);
            throw new RuntimeException("show binlog position failed");
        }
        return binlogPosition;
    }

    private String queryServerUUID(DruidDataSource druidDataSource, DataSourceType dataSourceType) {
        String serverUUID = "";
        try {
            String queryServerUUIDSql;
            if (DataSourceType.MariaDB.equals(dataSourceType)) {
                queryServerUUIDSql = SQL_SELECT_SERVER_UUID_IN_MARIADB;
            } else {
                queryServerUUIDSql = SQL_SHOW_SERVER_UUID_IN_MYSQL;
            }
            log.info("execute sql '{}' start.", queryServerUUIDSql);
            try (PreparedStatement preparedStatement = druidDataSource.getConnection().prepareStatement(queryServerUUIDSql)) {
                ResultSet resultSet = preparedStatement.executeQuery();
                if (resultSet.next()) {
                    log.info("execute sql '{}' result:{}", queryServerUUIDSql, resultSet);
                    serverUUID = resultSet.getString("server_uuid");
                    log.info("execute sql '{}',query server_uuid result:{}", queryServerUUIDSql, serverUUID);
                    return serverUUID;
                }
            }
        } catch (Exception e) {
            log.warn("select server_uuid failed,data source:{}", druidDataSource, e);
            throw new RuntimeException("select server_uuid failed");
        }
        return serverUUID;
    }

    private String queryGTID(DruidDataSource druidDataSource, DataSourceType dataSourceType) {
        String gitd = "";
        try {
            String queryGTIDSql;
            if (DataSourceType.MariaDB.equals(dataSourceType)) {
                queryGTIDSql = SQL_SELECT_GTID_IN_MARIADB;
            } else {
                queryGTIDSql = SQL_SELECT_GTID_IN_MYSQL;
            }
            log.info("execute sql '{}' start.", queryGTIDSql);
            try (PreparedStatement preparedStatement = druidDataSource.getConnection().prepareStatement(queryGTIDSql)) {
                ResultSet resultSet = preparedStatement.executeQuery();
                if (resultSet.next()) {
                    log.info("execute sql '{}' result:{}", queryGTIDSql, resultSet);
                    gitd = resultSet.getString("gtid");
                    log.info("execute sql '{}',select gitd result:{}", queryGTIDSql, gitd);
                    return gitd;
                }
            }
        } catch (Exception e) {
            log.warn("select gtid failed,data source:{}", druidDataSource, e);
            // when db server not open gitd mode, ignore gtid query exception
            //throw new RuntimeException("select gtid failed");
        }
        return gitd;
    }
}
