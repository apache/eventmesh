package org.apache.eventmesh.connector.dledger;

import org.apache.commons.pool2.PooledObjectFactory;
import org.apache.commons.pool2.impl.GenericObjectPool;

import io.openmessaging.storage.dledger.client.DLedgerClient;

public class DLedgerClientPool extends GenericObjectPool<DLedgerClient> {
    public DLedgerClientPool(PooledObjectFactory<DLedgerClient> factory) {
        super(factory);
    }
}
