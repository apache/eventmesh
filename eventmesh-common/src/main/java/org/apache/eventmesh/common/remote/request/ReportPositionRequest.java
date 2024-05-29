package org.apache.eventmesh.common.remote.request;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.eventmesh.common.remote.JobState;
import org.apache.eventmesh.common.remote.job.DataSourceType;
import org.apache.eventmesh.common.remote.offset.RecordPosition;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class ReportPositionRequest extends BaseRemoteRequest {

    private String jobID;

    private List<RecordPosition> recordPositionList;

    private JobState state;

    private String address;

    private DataSourceType dataSourceType;
}
