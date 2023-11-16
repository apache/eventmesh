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

package org.apache.eventmesh.connector.wecom.sink.connector;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class SendMessageDTO {

    @JsonProperty("errcode")
    private int errorCode;

    @JsonProperty("errmsg")
    private String errorMessage;

    @JsonProperty("invaliduser")
    private String invalidUser;

    @JsonProperty("invalidparty")
    private String invalidParty;

    @JsonProperty("invalidtag")
    private String invalidTag;

    @JsonProperty("unlicenseduser")
    private String unlicensedUser;

    @JsonProperty("msgid")
    private String messageId;

    @JsonProperty("response_code")
    private String responseCode;
}