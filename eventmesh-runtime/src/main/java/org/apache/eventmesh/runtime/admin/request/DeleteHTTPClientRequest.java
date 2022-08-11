package org.apache.eventmesh.runtime.admin.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class DeleteHTTPClientRequest {
    public String url;

    @JsonCreator
    public DeleteHTTPClientRequest(
            @JsonProperty("url") String url
    ) {
        super();
        this.url = url;
    }
}
