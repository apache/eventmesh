package org.apache.eventmesh.runtime.admin.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class DeleteGrpcClientRequest {
    public String url;

    @JsonCreator
    public DeleteGrpcClientRequest(
            @JsonProperty("url") String url
    ) {
        super();
        this.url = url;
    }
}
