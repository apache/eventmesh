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

package org.apache.eventmesh.connector.lark;

import org.apache.eventmesh.common.config.connector.lark.SinkConnectorConfig;

import org.apache.commons.lang3.StringUtils;

import com.lark.oapi.service.im.v1.enums.ReceiveIdTypeEnum;

public class ConfigUtils {

    public static void validateSinkConfiguration(SinkConnectorConfig sinkConnectorConfig) {
        // validate blank
        if (StringUtils.isAnyBlank(sinkConnectorConfig.getAppId(), sinkConnectorConfig.getAppSecret(), sinkConnectorConfig.getReceiveId())) {
            throw new IllegalArgumentException("appId or appSecret or receiveId is blank,please check it.");
        }

        // validate receiveIdType
        if (!StringUtils.containsAny(sinkConnectorConfig.getReceiveIdType(), ReceiveIdTypeEnum.CHAT_ID.getValue(),
            ReceiveIdTypeEnum.EMAIL.getValue(),
            ReceiveIdTypeEnum.OPEN_ID.getValue(),
            ReceiveIdTypeEnum.USER_ID.getValue(),
            ReceiveIdTypeEnum.UNION_ID.getValue())) {
            throw new IllegalArgumentException(
                String.format("sinkConnectorConfig.receiveIdType=[%s], Invalid.", sinkConnectorConfig.getReceiveIdType()));
        }
    }
}
