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

import org.apache.eventmesh.api.SendResult;
import org.apache.eventmesh.connector.dledger.exception.DLedgerConnectorException;

import org.apache.commons.pool2.impl.GenericObjectPool;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.openmessaging.storage.dledger.client.DLedgerClient;
import io.openmessaging.storage.dledger.entry.DLedgerEntry;
import io.openmessaging.storage.dledger.protocol.AppendEntryResponse;
import io.openmessaging.storage.dledger.protocol.DLedgerResponseCode;
import io.openmessaging.storage.dledger.protocol.GetEntriesResponse;
import io.openmessaging.storage.dledger.protocol.LeadershipTransferResponse;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DLedgerClientPool extends GenericObjectPool<DLedgerClient> {
    private static final Logger LOGGER = LoggerFactory.getLogger(DLedgerClientPool.class);

    public static volatile DLedgerClientPool clientPool;

    private DLedgerClientPool(DLedgerClientFactory factory, int size) {
        super(factory);
        setMaxTotal(size);
        setMinIdle(1);
    }

    public static DLedgerClientPool getInstance() {
        if (clientPool == null) {
            throw new DLedgerConnectorException("DLedgerClientPool hasn't created.");
        }
        return clientPool;
    }

    public static DLedgerClientPool getInstance(DLedgerClientFactory factory, int size) {
        if (clientPool == null) {
            synchronized (DLedgerClientPool.class) {
                if (clientPool == null) {
                    clientPool = new DLedgerClientPool(factory, size);
                    return clientPool;
                }
            }
        }
        return clientPool;
    }

    public SendResult append(String topic, byte[] body) throws Exception {
        AppendEntryResponse response = clientPool.borrowObject().append(body);
        if (DLedgerResponseCode.SUCCESS.getCode() != response.getCode()) {
            throw new DLedgerConnectorException(String.format("Error code: %d", response.getCode()));
        }

        SendResult sendResult = new SendResult();
        sendResult.setTopic(topic);
        sendResult.setMessageId(String.valueOf(response.getIndex()));
        return sendResult;
    }

    public List<DLedgerEntry> get(long index) throws Exception {
        GetEntriesResponse response = clientPool.borrowObject().get(index);
        if (DLedgerResponseCode.SUCCESS.getCode() != response.getCode()) {
            throw new DLedgerConnectorException(String.format("Error code: %d", response.getCode()));
        }
        return response.getEntries();
    }

    public boolean leadershipTransfer(String curLeaderId, String transfereeId, long term) throws Exception {
        LeadershipTransferResponse response = clientPool.borrowObject().leadershipTransfer(curLeaderId, transfereeId, term);
        if (DLedgerResponseCode.SUCCESS.getCode() != response.getCode()) {
            throw new DLedgerConnectorException(String.format("Error code: %d", response.getCode()));
        }
        return true;
    }
}
