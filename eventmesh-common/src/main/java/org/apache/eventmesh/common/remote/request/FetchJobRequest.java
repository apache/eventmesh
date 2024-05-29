package org.apache.eventmesh.common.remote.request;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class FetchJobRequest extends BaseRemoteRequest {
    private String jobID;
}
