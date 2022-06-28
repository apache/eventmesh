package org.apache.eventmesh.connector.dledger;

import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.DestroyMode;
import org.apache.commons.pool2.PooledObject;

import io.openmessaging.storage.dledger.client.DLedgerClient;

public class DLedgerClientFactory extends BasePooledObjectFactory<DLedgerClient> {
    @Override
    public DLedgerClient create() throws Exception {
        return null;
    }

    @Override
    public PooledObject<DLedgerClient> wrap(DLedgerClient obj) {
        return null;
    }

    @Override
    public void destroyObject(PooledObject<DLedgerClient> p) throws Exception {
        super.destroyObject(p);
    }
}
