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

package org.apache.eventmesh.common.config.connector.lark;

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
     */
    private String sinkAsync = "true";

    private String maxRetryTimes = "3";

    private String retryDelayInMills = "1000";

}
