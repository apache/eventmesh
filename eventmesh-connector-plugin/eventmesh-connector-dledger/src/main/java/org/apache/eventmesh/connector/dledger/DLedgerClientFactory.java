package org.apache.eventmesh.connector.dledger;

import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.DestroyMode;
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
