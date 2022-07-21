package org.apache.eventmesh.registry.etcd.wrapper;

import io.etcd.jetcd.*;

public class EtcdClientWrapper implements Client {

    private volatile Client client;

    public EtcdClientWrapper(Client client) {
        this.client = client;
    }

    @Override
    public Auth getAuthClient() {
        return client.getAuthClient();
    }

    @Override
    public KV getKVClient() {
        return client.getKVClient();
    }

    @Override
    public Cluster getClusterClient() {
        return client.getClusterClient();
    }

    @Override
    public Maintenance getMaintenanceClient() {
        return client.getMaintenanceClient();
    }

    @Override
    public Lease getLeaseClient() {
        return client.getLeaseClient();
    }

    @Override
    public Watch getWatchClient() {
        return client.getWatchClient();
    }

    @Override
    public Lock getLockClient() {
        return client.getLockClient();
    }

    @Override
    public void close() {
        client.close();
    }

}