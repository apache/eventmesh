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
import org.apache.eventmesh.admin.server.web.service.position.PositionHandler;
import org.apache.eventmesh.common.protocol.grpc.adminserver.Metadata;
import org.apache.eventmesh.common.remote.datasource.DataSourceType;
import org.apache.eventmesh.common.remote.offset.RecordPosition;
import org.apache.eventmesh.common.remote.offset.canal.CanalRecordOffset;
import org.apache.eventmesh.common.remote.offset.canal.CanalRecordPartition;
import org.apache.eventmesh.common.remote.request.FetchPositionRequest;
import org.apache.eventmesh.common.remote.request.ReportPositionRequest;
import org.apache.eventmesh.common.utils.JsonUtils;

import org.apache.commons.lang3.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.LockSupport;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class MysqlPositionHandler extends PositionHandler {
    private static final int RETRY_TIMES = 3;

    private final long retryPeriod = Duration.ofMillis(500).toNanos();

    @Autowired
    EventMeshMysqlPositionService positionService;

    @Autowired
    EventMeshPositionReporterHistoryService historyService;

    @Override
    protected DataSourceType getSourceType() {
        return DataSourceType.MYSQL;
    }

    private boolean isNotForward(EventMeshMysqlPosition now, EventMeshMysqlPosition old) {
        if (StringUtils.isNotBlank(old.getJournalName()) && old.getJournalName().equals(now.getJournalName())
            && old.getPosition() >= now.getPosition()) {
            log.info("job [{}] report position [{}] by runtime [{}] less than db position [{}] journal name [{}] by [{}]",
                now.getJobID(), now.getPosition(), now.getAddress(), now.getJournalName(), old.getPosition(), old.getAddress());
            return true;
        }
        return false;
    }

    public boolean saveOrUpdateByJob(EventMeshMysqlPosition position) {
        for (int i = 0; i < RETRY_TIMES; i++) {
            EventMeshMysqlPosition old = positionService.getOne(Wrappers.<EventMeshMysqlPosition>query().eq("jobId",
                position.getJobID()));
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
                if (!positionService.update(position, Wrappers.<EventMeshMysqlPosition>update().eq("updateTime",
                    old.getUpdateTime()).eq("jobID", old.getJobID()))) {
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
            position.setJobID(Integer.parseInt(request.getJobID()));
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
        List<EventMeshMysqlPosition> positionList = positionService.list(Wrappers.<EventMeshMysqlPosition>query().eq("jobID",
            request.getJobID()));
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
}
