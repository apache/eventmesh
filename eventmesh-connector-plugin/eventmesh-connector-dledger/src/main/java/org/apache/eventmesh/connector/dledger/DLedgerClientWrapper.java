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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.openmessaging.storage.dledger.client.DLedgerClient;
import io.openmessaging.storage.dledger.entry.DLedgerEntry;
import io.openmessaging.storage.dledger.protocol.AppendEntryResponse;
import io.openmessaging.storage.dledger.protocol.GetEntriesResponse;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DLedgerClientWrapper {
    private static final Logger LOGGER = LoggerFactory.getLogger(DLedgerClientWrapper.class);

    private final DLedgerClientPool clientPool;

    private final String group;
    private final String peers;

    public DLedgerClientWrapper(String group, String peers) {
        this.group = group;
        this.peers = peers;
        clientPool = new DLedgerClientPool(new DLedgerClientFactory());
    }

    public void startup() throws Exception {
        clientPool.borrowObject().startup();
    }

    public void showdown() throws Exception {
        clientPool.borrowObject().shutdown();
    }

    public long add(DLedgerEventWrapper eventWrapper) throws Exception {
        AppendEntryResponse response = clientPool.borrowObject().append(eventWrapper.toString().getBytes(StandardCharsets.UTF_8));
        long index = response.getIndex();
        return index;
    }

    public List<DLedgerEventWrapper> get(long index) throws Exception {
        GetEntriesResponse response = clientPool.borrowObject().get(index);
        List<DLedgerEntry> entries = response.getEntries();
        List<DLedgerEventWrapper> eventWrapperList = new ArrayList<>(entries.size());
        for (DLedgerEntry entry : entries) {
            DLedgerEventWrapper eventWrapper = new DLedgerEventWrapper(entry);
            eventWrapperList.add(eventWrapper);
        }
        return eventWrapperList;
    }
}
