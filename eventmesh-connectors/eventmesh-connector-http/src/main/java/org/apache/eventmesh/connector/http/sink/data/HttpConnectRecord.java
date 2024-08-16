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

package org.apache.eventmesh.connector.http.sink.data;

import org.apache.eventmesh.common.remote.offset.http.HttpRecordOffset;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

/**
 * a special ConnectRecord for HttpSinkConnector
 */
@Getter
@Builder
public class HttpConnectRecord implements Serializable {

    private static final long serialVersionUID = 5271462532332251473L;

    /**
     * The unique identifier for the HttpConnectRecord
     */
    private final String httpRecordId = UUID.randomUUID().toString();

    /**
     * The time when the HttpConnectRecord was created
     */
    private LocalDateTime createTime;

    /**
     * The type of the HttpConnectRecord
     */
    private String type;

    /**
     * The event id of the HttpConnectRecord
     */
    private String eventId;

    /**
     * The ConnectRecord to be sent
     */
    private ConnectRecord data;

    @Override
    public String toString() {
        return "HttpConnectRecord{"
            + "createTime=" + createTime
            + ", httpRecordId='" + httpRecordId
            + ", type='" + type
            + ", eventId='" + eventId
            + ", data=" + data
            + '}';
    }

    /**
     * Convert ConnectRecord to HttpConnectRecord
     *
     * @param record the ConnectRecord to convert
     * @return the converted HttpConnectRecord
     */
    public static HttpConnectRecord convertConnectRecord(ConnectRecord record, String type) {
        Map<String, ?> offsetMap = new HashMap<>();
        if (record != null && record.getPosition() != null && record.getPosition().getRecordOffset() != null) {
            offsetMap = ((HttpRecordOffset) record.getPosition().getRecordOffset()).getOffsetMap();
        }
        String offset = "0";
        if (!offsetMap.isEmpty()) {
            offset = offsetMap.values().iterator().next().toString();
        }
        return HttpConnectRecord.builder()
            .type(type)
            .eventId(type + "-" + offset)
            .data(record)
            .build();
    }
}
