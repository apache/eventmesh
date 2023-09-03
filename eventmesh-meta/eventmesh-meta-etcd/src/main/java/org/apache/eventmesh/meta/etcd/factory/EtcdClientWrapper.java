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

package org.apache.eventmesh.meta.etcd.factory;


import io.etcd.jetcd.Auth;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.Cluster;
import io.etcd.jetcd.KV;
import io.etcd.jetcd.Lease;
import io.etcd.jetcd.Lock;
import io.etcd.jetcd.Maintenance;
import io.etcd.jetcd.Watch;


class EtcdClientWrapper implements Client {

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