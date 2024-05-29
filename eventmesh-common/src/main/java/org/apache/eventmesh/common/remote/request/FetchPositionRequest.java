package org.apache.eventmesh.common.remote.request;

import org.apache.eventmesh.common.remote.job.DataSourceType;
import org.apache.eventmesh.common.remote.offset.RecordPosition;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class FetchPositionRequest extends BaseRemoteRequest {

    private String jobID;

    private String address;

    private RecordPosition recordPosition;

    private DataSourceType dataSourceType;

}
