package org.apache.eventmesh.runtime.admin.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class DeleteTopicRequest {
    public String name;

    @JsonCreator
    public DeleteTopicRequest(
            @JsonProperty("name") String name
    ) {
        super();
        this.name = name;
    }
}
