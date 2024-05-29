package com.apache.eventmesh.admin.server.web.service.position.impl;

import com.apache.eventmesh.admin.server.web.db.entity.EventMeshMysqlPosition;
import com.apache.eventmesh.admin.server.web.db.entity.EventMeshPositionReporterHistory;
import com.apache.eventmesh.admin.server.web.db.service.EventMeshMysqlPositionService;
import com.apache.eventmesh.admin.server.web.db.service.EventMeshPositionReporterHistoryService;
import com.apache.eventmesh.admin.server.web.service.position.PositionHandler;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.extern.slf4j.Slf4j;
import org.apache.eventmesh.common.protocol.grpc.adminserver.Metadata;
import org.apache.eventmesh.common.remote.job.DataSourceType;
import org.apache.eventmesh.common.remote.offset.RecordPosition;
import org.apache.eventmesh.common.remote.offset.canal.CanalRecordOffset;
import org.apache.eventmesh.common.remote.offset.canal.CanalRecordPartition;
import org.apache.eventmesh.common.remote.request.FetchPositionRequest;
import org.apache.eventmesh.common.remote.request.ReportPositionRequest;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class MysqlPositionHandler extends PositionHandler {
    @Autowired
    EventMeshMysqlPositionService positionService;

    @Autowired
    EventMeshPositionReporterHistoryService historyService;

    @Override
    protected DataSourceType getSourceType() {
        return DataSourceType.MYSQL;
    }

    public boolean saveOrUpdateByJob(EventMeshMysqlPosition position) {
        EventMeshMysqlPosition old = positionService.getOne(Wrappers.<EventMeshMysqlPosition>query().eq("jobId",
                position.getJobID()));
        if (old == null) {
            return positionService.save(position);
        } else {
            if (old.getPosition() >= position.getPosition()) {
                log.info("job [{}] report position [{}] by runtime [{}] less than db position [{}] by [{}]",
                        position.getJobID(), position.getPosition(), position.getAddress(), old.getPosition(), old.getAddress());
                return true;
            }
            try {
                return positionService.update(position, Wrappers.<EventMeshMysqlPosition>update().eq("updateTime",
                        old.getUpdateTime()));
            } finally {
                if (old.getAddress()!= null && !old.getAddress().equals(position.getAddress())) {
                    EventMeshPositionReporterHistory history = new EventMeshPositionReporterHistory();
                    history.setRecord(JsonUtils.toJSONString(position));
                    history.setJob(old.getJobID());
                    history.setAddress(old.getAddress());
                    log.info("job [{}] position reporter changed old [{}], now [{}]", position.getJobID(), old, position);
                    try {
                        historyService.save(history);
                    } catch (Exception e) {
                        log.warn("save job [{}] mysql position reporter changed history fail, now reporter [{}], old " +
                                "[{}]", position.getJobID(), position.getAddress(), old.getAddress(), e);
                    }
                }
            }
        }
    }

    @Override
    public boolean handler(ReportPositionRequest request, Metadata metadata) {
        for (int i = 0; i < 3; i++) {
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
                CanalRecordOffset offset = (CanalRecordOffset) recordPosition.getRecordOffset();
                CanalRecordPartition partition = (CanalRecordPartition) recordPosition.getRecordPartition();
                EventMeshMysqlPosition position = new EventMeshMysqlPosition();
                position.setJobID(Integer.parseInt(request.getJobID()));
                position.setAddress(request.getAddress());
                if (offset != null) {
                    position.setPosition(offset.getOffset());
                }
                if (partition != null) {
                    position.setTimestamp(partition.getTimeStamp());
                    position.setJournalName(partition.getJournalName());
                }
                if (!saveOrUpdateByJob(position)) {
                    log.warn("update job position fail [{}]", request);
                    return false;
                }
                return true;
            } catch (DuplicateKeyException e) {
                log.warn("concurrent report position job [{}], it will try again", request.getJobID());
            } catch (Exception e) {
                log.warn("save position job [{}] fail", request.getJobID(), e);
                return false;
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException ignore) {
                log.warn("save position thread interrupted, [{}]", request);
                return true;
            }
        }
        return false;
    }

    @Override
    public RecordPosition handler(FetchPositionRequest request, Metadata metadata) {
        EventMeshMysqlPosition position = positionService.getOne(Wrappers.<EventMeshMysqlPosition>query().eq("jobID"
                , request.getJobID()));
        RecordPosition recordPosition = null;
        if (position != null) {
            CanalRecordPartition partition = new CanalRecordPartition();
            partition.setTimeStamp(position.getTimestamp());
            partition.setJournalName(position.getJournalName());
            CanalRecordOffset offset = new CanalRecordOffset();
            offset.setOffset(position.getPosition());
            recordPosition = new RecordPosition();
            recordPosition.setRecordPartition(partition);
            recordPosition.setRecordOffset(offset);
        }
        return recordPosition;
    }
}
