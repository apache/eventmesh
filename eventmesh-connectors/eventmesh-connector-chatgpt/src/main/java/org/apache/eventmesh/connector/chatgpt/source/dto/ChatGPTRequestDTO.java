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

package org.apache.eventmesh.connector.chatgpt.source.dto;

import org.apache.eventmesh.connector.chatgpt.source.enums.ChatGPTRequestType;

import java.time.ZonedDateTime;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChatGPTRequestDTO {

    private String requestType = ChatGPTRequestType.CHAT.name();

    private String source;

    private String subject;

    @JsonProperty("datacontenttype")
    private String dataContentType;

    private String type;

    private String text;

    private String fields;

    @JsonInclude
    private String id = UUID.randomUUID().toString();

    @JsonInclude
    private String time = ZonedDateTime.now().toOffsetDateTime().toString();

    public String getFields() {
        return fields.replace(";", "\n");
    }
}
