package org.apache.eventmesh.common.remote.response;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.eventmesh.common.remote.JobState;
import org.apache.eventmesh.common.remote.exception.ErrorCode;
import org.apache.eventmesh.common.remote.job.JobTransportType;
import org.apache.eventmesh.common.remote.offset.RecordPosition;

import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
public class FetchJobResponse extends BaseRemoteResponse {

    private Integer id;

    private String name;

    private JobTransportType transportType;

    private Map<String, Object> sourceConnectorConfig;

    private String sourceConnectorDesc;

    private Map<String, Object> sinkConnectorConfig;

    private String sinkConnectorDesc;

    private RecordPosition position;

    private JobState state;

    public static FetchJobResponse successResponse() {
        FetchJobResponse response = new FetchJobResponse();
        response.setSuccess(true);
        response.setErrorCode(ErrorCode.SUCCESS);
        return response;
    }

    public static FetchJobResponse failResponse(int code, String desc) {
        FetchJobResponse response = new FetchJobResponse();
        response.setSuccess(false);
        response.setErrorCode(code);
        response.setDesc(desc);
        return response;
    }

}
