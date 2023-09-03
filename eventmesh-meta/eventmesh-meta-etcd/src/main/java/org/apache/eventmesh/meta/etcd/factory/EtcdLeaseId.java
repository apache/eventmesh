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

import java.util.concurrent.atomic.AtomicBoolean;

import io.etcd.jetcd.ClientBuilder;

class EtcdLeaseId {

    private long leaseId;

    private AtomicBoolean renewalLock = new AtomicBoolean();

    private EtcdClientWrapper clientWrapper;

    private ClientBuilder clientBuilder;

    private String url;

    private long ttl;

    private EtcdStreamObserver etcdStreamObserver;

    public AtomicBoolean getRenewalLock() {
        return renewalLock;
    }

    public void setRenewalLock(AtomicBoolean renewalLock) {
        this.renewalLock = renewalLock;
    }

    public EtcdClientWrapper getClientWrapper() {
        return clientWrapper;
    }

    public void setClientWrapper(EtcdClientWrapper clientWrapper) {
        this.clientWrapper = clientWrapper;
    }

    public ClientBuilder getClientBuilder() {
        return clientBuilder;
    }

    public void setClientBuilder(ClientBuilder clientBuilder) {
        this.clientBuilder = clientBuilder;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public EtcdStreamObserver getEtcdStreamObserver() {
        return etcdStreamObserver;
    }

    public void setEtcdStreamObserver(EtcdStreamObserver etcdStreamObserver) {
        this.etcdStreamObserver = etcdStreamObserver;
    }

    public long getLeaseId() {
        return leaseId;
    }

    public void setLeaseId(long leaseId) {
        this.leaseId = leaseId;
    }
}

