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

package org.apache.eventmesh.connector.dledger.clientpool;

import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;

import io.openmessaging.storage.dledger.client.DLedgerClient;

public class DLedgerClientFactory extends BasePooledObjectFactory<DLedgerClient> {
    private final String group;
    private final String peers;

    public DLedgerClientFactory(String group, String peers) {
        this.group = group;
        this.peers = peers;
    }

    @Override
    public DLedgerClient create() {
        DLedgerClient client = new DLedgerClient(group, peers);
        client.startup();
        return client;
    }

    @Override
    public PooledObject<DLedgerClient> wrap(DLedgerClient obj) {
        return new DefaultPooledObject<>(obj);
    }

    @Override
    public boolean validateObject(PooledObject<DLedgerClient> p) {
        if (p == null) {
            return false;
        }
        DLedgerClient client = p.getObject();
        return client != null;
    }

    @Override
    public void destroyObject(PooledObject<DLedgerClient> p) throws Exception {
        if (p == null) {
            return;
        }
        DLedgerClient client = p.getObject();
        client.shutdown();
        super.destroyObject(p);
    }
}
