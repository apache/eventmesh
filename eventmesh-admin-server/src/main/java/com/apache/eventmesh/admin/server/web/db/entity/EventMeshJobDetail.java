package com.apache.eventmesh.admin.server.web.db.entity;

import lombok.Data;
import org.apache.eventmesh.common.remote.JobState;
import org.apache.eventmesh.common.remote.job.JobTransportType;
import org.apache.eventmesh.common.remote.offset.RecordPosition;

import java.util.Map;

@Data
public class EventMeshJobDetail {
    private Integer id;

    private String name;

    private JobTransportType transportType;

    private Map<String, Object> sourceConnectorConfig;

    private String sourceConnectorDesc;

    private Map<String, Object> sinkConnectorConfig;

    private String sinkConnectorDesc;

    private RecordPosition position;

    private JobState state;
}
