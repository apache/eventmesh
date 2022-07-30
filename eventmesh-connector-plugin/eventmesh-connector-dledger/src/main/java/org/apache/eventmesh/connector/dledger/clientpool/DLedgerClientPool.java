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

import org.apache.eventmesh.api.SendResult;
import org.apache.eventmesh.connector.dledger.exception.DLedgerConnectorException;

import org.apache.commons.pool2.impl.GenericObjectPool;

import java.util.List;

import io.openmessaging.storage.dledger.client.DLedgerClient;
import io.openmessaging.storage.dledger.entry.DLedgerEntry;
import io.openmessaging.storage.dledger.protocol.AppendEntryResponse;
import io.openmessaging.storage.dledger.protocol.DLedgerResponseCode;
import io.openmessaging.storage.dledger.protocol.GetEntriesResponse;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DLedgerClientPool extends GenericObjectPool<DLedgerClient> {
    public static DLedgerClientPool CLIENT_POOL;

    private DLedgerClientPool(DLedgerClientFactory factory, int size) {
        super(factory);
        setMaxTotal(size);
        setMinIdle(1);
    }

    public SendResult append(String topic, byte[] body) throws Exception {
        AppendEntryResponse response = CLIENT_POOL.borrowObject().append(body);
        if (DLedgerResponseCode.SUCCESS.getCode() != response.getCode()) {
            throw new DLedgerConnectorException(String.format("Error code: %d", response.getCode()));
        }

        SendResult sendResult = new SendResult();
        sendResult.setTopic(topic);
        sendResult.setMessageId(String.valueOf(response.getIndex()));
        return sendResult;
    }

    public List<DLedgerEntry> get(long index) throws Exception {
        GetEntriesResponse response = CLIENT_POOL.borrowObject().get(index);
        if (DLedgerResponseCode.SUCCESS.getCode() != response.getCode()) {
            throw new DLedgerConnectorException(String.format("Error code: %d", response.getCode()));
        }
        return response.getEntries();
    }

    public static DLedgerClientPool getInstance() {
        if (CLIENT_POOL == null) {
            throw new DLedgerConnectorException("DLedgerClientPool hasn't created.");
        }
        return CLIENT_POOL;
    }

    public static synchronized DLedgerClientPool setupAndGetInstance(DLedgerClientFactory factory, int size) {
        if (CLIENT_POOL == null) {
            CLIENT_POOL = new DLedgerClientPool(factory, size);
            return CLIENT_POOL;
        }
        return CLIENT_POOL;
    }
}
