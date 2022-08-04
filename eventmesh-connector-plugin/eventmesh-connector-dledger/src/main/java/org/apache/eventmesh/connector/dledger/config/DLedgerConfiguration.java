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

package org.apache.eventmesh.connector.dledger.config;

import org.apache.commons.lang3.StringUtils;

import lombok.Getter;

@Getter
public class DLedgerConfiguration {
    public static String KEYS_EVENTMESH_DLEDGER_GROUP = "eventMesh.server.dledger.group";
    public static String KEYS_EVENTMESH_DLEDGER_PEERS = "eventMesh.server.dledger.peers";
    public static String KEYS_EVENTMESH_DLEDGER_CLIENTPOOL_SIZE = "eventMesh.server.dledger.clientPool.size";
    public static String KEYS_EVENTMESH_DLEDGER_QUEUE_SIZE = "eventMesh.server.dledger.queue.size";

    private String group = "default";
    private String peers = "n0-localhost:20911;n1-localhost:20912;n2-localhost:20913";
    private int clientPoolSize = 8;
    private int queueSize = 512;

    private DLedgerConfiguration() {
    }

    private void init() {
        String groupStr = DLedgerConfigurationWrapper.getProp(KEYS_EVENTMESH_DLEDGER_GROUP);
        if (StringUtils.isNotBlank(groupStr)) {
            group = StringUtils.trim(groupStr);
        }

        String peersStr = DLedgerConfigurationWrapper.getProp(KEYS_EVENTMESH_DLEDGER_PEERS);
        if (StringUtils.isNotBlank(peersStr)) {
            peers = StringUtils.trim(peersStr);
        }

        String clientPoolSizeStr = DLedgerConfigurationWrapper.getProp(KEYS_EVENTMESH_DLEDGER_CLIENTPOOL_SIZE);
        if (StringUtils.isNumeric(clientPoolSizeStr)) {
            clientPoolSize = Integer.parseInt(clientPoolSizeStr);
        }

        String queueSizeStr = DLedgerConfigurationWrapper.getProp(KEYS_EVENTMESH_DLEDGER_QUEUE_SIZE);
        if (StringUtils.isNumeric(queueSizeStr)) {
            queueSize = Integer.parseInt(queueSizeStr);
        }
    }

    private static DLedgerConfiguration INSTANCE = null;

    public static synchronized DLedgerConfiguration getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new DLedgerConfiguration();
            INSTANCE.init();
        }
        return INSTANCE;
    }
}
