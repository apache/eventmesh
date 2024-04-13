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

import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;

import org.apache.commons.lang3.StringUtils;

import com.lark.oapi.service.im.v1.enums.ReceiveIdTypeEnum;

import lombok.Data;

@Data
public class SinkConnectorConfig {

    private String connectorName = "larkSink";

    /**
     * Can not be blank
     */
    private String appId;

    /**
     * Can not be blank
     */
    private String appSecret;

    /**
     * The value is {@code open_id/user_id/union_id/email/chat_id}.
     * Recommend to use open_id.
     */
    private String receiveIdType = "open_id";

    /**
     * Can not be blank.And it needs to correspond to {@code receiveIdType}
     */
    private String receiveId;

    /**
     * When sinking CouldEvent to lark, choose to call
     * {@link org.apache.eventmesh.connector.lark.sink.ImServiceHandler#sink(ConnectRecord)}
     * or {@link org.apache.eventmesh.connector.lark.sink.ImServiceHandler#sinkAsync(ConnectRecord)}
     */
    private String sinkAsync = "true";

    private String maxRetryTimes = "3";

    private String retryDelayInMills = "1000";

    public void validateSinkConfiguration() {
        // validate blank
        if (StringUtils.isAnyBlank(appId, appSecret, receiveId)) {
            throw new IllegalArgumentException("appId or appSecret or receiveId is blank,please check it.");
        }

        // validate receiveIdType
        if (!StringUtils.containsAny(receiveIdType, ReceiveIdTypeEnum.CHAT_ID.getValue(),
            ReceiveIdTypeEnum.EMAIL.getValue(),
            ReceiveIdTypeEnum.OPEN_ID.getValue(),
            ReceiveIdTypeEnum.USER_ID.getValue(),
            ReceiveIdTypeEnum.UNION_ID.getValue())) {
            throw new IllegalArgumentException(
                String.format("sinkConnectorConfig.receiveIdType=[%s], Invalid.", receiveIdType));
        }
    }
}
