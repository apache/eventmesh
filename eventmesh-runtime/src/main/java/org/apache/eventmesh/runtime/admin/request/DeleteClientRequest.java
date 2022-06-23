package org.apache.eventmesh.runtime.admin.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class DeleteClientRequest {
    public String host;
    public int port;
    public String protocol;

    @JsonCreator
    public DeleteClientRequest(
            @JsonProperty("host") String host,
            @JsonProperty("port") int port,
            @JsonProperty("protocol") String protocol
    ) {
        super();
        this.host = host;
        this.port = port;
        this.protocol = protocol;
    }
}
