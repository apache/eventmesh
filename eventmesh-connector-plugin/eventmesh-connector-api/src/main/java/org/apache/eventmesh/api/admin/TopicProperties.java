package org.apache.eventmesh.api.admin;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class TopicProperties {
    public String name;
    public long messageCount;

    @JsonCreator
    public TopicProperties(
            @JsonProperty("name") String name,
            @JsonProperty("messageCount") long messageCount
    ) {
        super();
        this.name = name;
        this.messageCount = messageCount;
    }
}
