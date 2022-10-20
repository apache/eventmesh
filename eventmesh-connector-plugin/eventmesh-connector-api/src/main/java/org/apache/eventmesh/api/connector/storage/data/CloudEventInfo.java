/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.eventmesh.api.connector.storage.data;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

import lombok.Data;

@Data
public class CloudEventInfo {

    private Long cloudEventInfoId;

    private Long cloudEventId;

    private String cloudEventTopic;

    private String cloudEventStorageNodeAdress;

    private String cloudEventType;

    private String cloudEventProducerGroupName;

    private String cloudEventSource;

    private String cloudEventContentType;

    private Set<String> cloudEventEventTag;

    private Map<String, String> cloudEventExtensions;

    private String cloudEventData;

    private String cloudEventReplyData;

    private Map<String, String> cloudEventConsumeLocation;

    private CloudEventStateEnums cloudEventState;

    private CloudEventStateEnums cloudEventReplyState;

    private LocalDateTime cloudEventCreateTime;

    private LocalDateTime cloudEventConsumeTime;

    private LocalDateTime cloudEventOffsetTime;

}
