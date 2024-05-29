package org.apache.eventmesh.common.remote.response;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.eventmesh.common.remote.exception.ErrorCode;
import org.apache.eventmesh.common.remote.offset.RecordPosition;

@Data
@EqualsAndHashCode(callSuper = true)
public class FetchPositionResponse extends BaseRemoteResponse {

    private RecordPosition recordPosition;

    public static FetchPositionResponse successResponse() {
        FetchPositionResponse response = new FetchPositionResponse();
        response.setSuccess(true);
        response.setErrorCode(ErrorCode.SUCCESS);
        return response;
    }

    public static FetchPositionResponse successResponse(RecordPosition recordPosition) {
        FetchPositionResponse response = successResponse();
        response.setRecordPosition(recordPosition);
        return response;
    }

    public static FetchPositionResponse failResponse(int code, String desc) {
        FetchPositionResponse response = new FetchPositionResponse();
        response.setSuccess(false);
        response.setErrorCode(code);
        response.setDesc(desc);
        return response;
    }

}
