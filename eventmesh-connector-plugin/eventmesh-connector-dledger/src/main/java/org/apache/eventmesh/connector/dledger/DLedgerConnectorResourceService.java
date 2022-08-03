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

package org.apache.eventmesh.connector.dledger;

import org.apache.eventmesh.api.connector.ConnectorResourceService;
import org.apache.eventmesh.connector.dledger.broker.DLedgerTopicIndexesStore;
import org.apache.eventmesh.connector.dledger.clientpool.DLedgerClientFactory;
import org.apache.eventmesh.connector.dledger.clientpool.DLedgerClientPool;
import org.apache.eventmesh.connector.dledger.config.DLedgerConfiguration;

public class DLedgerConnectorResourceService implements ConnectorResourceService {
    @Override
    public void init() throws Exception {
        DLedgerConfiguration configuration = DLedgerConfiguration.getInstance();

        // init Broker
        DLedgerTopicIndexesStore store = DLedgerTopicIndexesStore.setUpAndGetInstance(configuration.getQueueSize());

        // init DLedgerClientPool
        DLedgerClientFactory clientFactory =
            new DLedgerClientFactory(configuration.getGroup(), configuration.getPeers());
        DLedgerClientPool clientPool =
            DLedgerClientPool.setupAndGetInstance(clientFactory, configuration.getClientPoolSize());
        clientPool.preparePool();
    }

    @Override
    public void release() throws Exception {

    }
}
