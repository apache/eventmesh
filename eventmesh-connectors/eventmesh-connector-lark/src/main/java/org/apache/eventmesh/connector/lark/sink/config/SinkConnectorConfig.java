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

package org.apache.eventmesh.connector.lark.sink.config;

import com.lark.oapi.service.im.v1.enums.ReceiveIdTypeEnum;

import lombok.Data;

@Data
public class SinkConnectorConfig {

    private String connectorName = "larkSink";

    private String appId;

    private String appSecret;

    /**
     * The value is {@code open_id/user_id/union_id/email/chat_id} <br>
     * Recommend to use open_id
     */
    private String receiveIdType = "open_id";

    private String receiveId;

    private String atUsers;

    private String atAll = "false";

    private String maxRetryTimes = "3";

    private String retryDelayInMills = "1000";

    public void validateReceiveIdType() {
        if (ReceiveIdTypeEnum.CHAT_ID.getValue().equals(receiveIdType)
                || ReceiveIdTypeEnum.EMAIL.getValue().equals(receiveIdType)
                || ReceiveIdTypeEnum.OPEN_ID.getValue().equals(receiveIdType)
                || ReceiveIdTypeEnum.USER_ID.getValue().equals(receiveIdType)
                || ReceiveIdTypeEnum.UNION_ID.getValue().equals(receiveIdType)) {
            return;
        }
        throw new IllegalArgumentException(String.format("sinkConnectorConfig.receiveIdType=[%s] Invalid", receiveIdType));
    }
}
