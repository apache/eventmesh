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

package org.apache.eventmesh.connector.s3.source;

import org.apache.eventmesh.connector.feishu.sink.config.FeishuSinkConfig;
import org.apache.eventmesh.connector.feishu.sink.config.SinkConnectorConfig;
import org.apache.eventmesh.connector.feishu.sink.connector.FeishuSinkConnector;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;


@Disabled
public class FeishuSinkConnectorTest {

    private static final FeishuSinkConfig sinkConfig;

    private static final SinkConnectorConfig SOURCE_CONNECTOR_CONFIG;

    static {
        sinkConfig = new FeishuSinkConfig();
        SOURCE_CONNECTOR_CONFIG = new SinkConnectorConfig();
        SOURCE_CONNECTOR_CONFIG.setConnectorName("FeishuSinkConnector");
        SOURCE_CONNECTOR_CONFIG.setAppId("xxx");
        SOURCE_CONNECTOR_CONFIG.setAppSecret("xxx");
        SOURCE_CONNECTOR_CONFIG.setReceiveId("xxx");
        SOURCE_CONNECTOR_CONFIG.setReceiveIdType("open_id");
        sinkConfig.setConnectorConfig(SOURCE_CONNECTOR_CONFIG);
    }


    @Test
    public void testFeishuSinkConnector() throws Exception {
        FeishuSinkConnector feishuSinkConnector = new FeishuSinkConnector();
        feishuSinkConnector.init(sinkConfig);
        feishuSinkConnector.start();
        List<ConnectRecord> connectRecords = new ArrayList<>();
        connectRecords.add(new ConnectRecord(null,null,0L,"test"));
        feishuSinkConnector.put(connectRecords);
    }


}
