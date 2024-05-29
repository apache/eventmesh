package org.apache.eventmesh.common.remote.request;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ReportHeartBeatRequest extends BaseRemoteRequest {

    private String address;

    private String reportedTimeStamp;

    private String jobID;
}
