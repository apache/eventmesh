package org.apache.eventmesh.api.connector.storage.data;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

import lombok.Data;

@Data
public class CloudEventInfo {

    private Long cloudEventId;

    private Long id;

    private String cloudEventType;

    private String producerGroupName;

    private LocalDateTime createTime;

    private Set<String> eventTag;

    private Map<String, String> eventExtensions;

    private String eventData;

    private String replyData;

    private Map<String, String> consumeLocation;

    private CloudEventStateEnums cloudEventState;

    private CloudEventStateEnums replyState;

    private LocalDateTime updateTime;

    private LocalDateTime consumeTime;

}
