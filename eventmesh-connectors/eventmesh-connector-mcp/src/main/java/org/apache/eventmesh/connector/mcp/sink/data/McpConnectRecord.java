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

package org.apache.eventmesh.connector.mcp.sink.data;

import lombok.Builder;
import lombok.Getter;
import org.apache.eventmesh.common.remote.offset.http.HttpRecordOffset;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.KeyValue;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * a special ConnectRecord for HttpSinkConnector
 */
@Getter
@Builder
public class McpConnectRecord implements Serializable {

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

    private Object data;

    private KeyValue extensions;

    @Override
    public String toString() {
        return "HttpConnectRecord{"
            + "createTime=" + createTime
            + ", httpRecordId='" + httpRecordId
            + ", type='" + type
            + ", eventId='" + eventId
            + ", data=" + data
            + ", extensions=" + extensions
            + '}';
    }

    /**
     * Convert ConnectRecord to HttpConnectRecord
     *
     * @param record the ConnectRecord to convert
     * @return the converted HttpConnectRecord
     */
    public static McpConnectRecord convertConnectRecord(ConnectRecord record, String type) {
        Map<String, ?> offsetMap = new HashMap<>();
        if (record != null && record.getPosition() != null && record.getPosition().getRecordOffset() != null) {
            if (HttpRecordOffset.class.equals(record.getPosition().getRecordOffsetClazz())) {
                offsetMap = ((HttpRecordOffset) record.getPosition().getRecordOffset()).getOffsetMap();
            }
        }
        String offset = "0";
        if (!offsetMap.isEmpty()) {
            offset = offsetMap.values().iterator().next().toString();
        }
        if (record.getData() instanceof byte[]) {
            String data = Base64.getEncoder().encodeToString((byte[]) record.getData());
            record.addExtension("isBase64", true);
            return McpConnectRecord.builder()
                .type(type)
                .createTime(LocalDateTime.now())
                .eventId(type + "-" + offset)
                .data(data)
                .extensions(record.getExtensions())
                .build();
        } else {
            record.addExtension("isBase64", false);
            return McpConnectRecord.builder()
                .type(type)
                .createTime(LocalDateTime.now())
                .eventId(type + "-" + offset)
                .data(record.getData())
                .extensions(record.getExtensions())
                .build();
        }
    }

}
