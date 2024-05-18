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

import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import lombok.Builder;
import lombok.Data;

/**
 * a special ConnectRecord for HttpSinkConnector
 */
@Data
@Builder
public class HttpConnectRecord {

    private String type;

    private String time;

    private String uuid;

    private String eventId;

    private ConnectRecord data;

    /**
     * Convert ConnectRecord to HttpConnectRecord
     *
     * @param record the ConnectRecord to convert
     * @return the converted HttpConnectRecord
     */
    public static HttpConnectRecord convertConnectRecord(ConnectRecord record, String type) {
        Map<String, ?> offsetMap = record.getPosition().getOffset().getOffset();
        String offset = "0";
        if (!offsetMap.isEmpty()) {
            offset = offsetMap.values().iterator().next().toString();
        }
        return HttpConnectRecord.builder()
            .type(type)
            .time(LocalDateTime.now().toString())
            .uuid(UUID.randomUUID().toString())
            .eventId(type + "-" + offset)
            .data(record)
            .build();
    }

}
