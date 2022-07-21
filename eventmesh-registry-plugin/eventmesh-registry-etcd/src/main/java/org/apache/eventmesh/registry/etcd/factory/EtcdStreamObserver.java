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

package org.apache.eventmesh.registry.etcd.factory;

import io.etcd.jetcd.Client;
import io.etcd.jetcd.lease.LeaseKeepAliveResponse;
import io.etcd.jetcd.options.LeaseOption;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EtcdStreamObserver implements StreamObserver<LeaseKeepAliveResponse> {

    private static final Logger logger = LoggerFactory.getLogger(EtcdStreamObserver.class);

    private EtcdLeaseId etcdLeaseId;

    @Override
    public void onNext(LeaseKeepAliveResponse value) {
    }

    @Override
    public void onError(Throwable t) {
        logger.error("EtcdStreamObserver error {}", t.getMessage(), t);
        this.createLeaseId(etcdLeaseId);
    }

    @Override
    public void onCompleted() {
        logger.info("EtcdStreamObserver completed");
        this.createLeaseId(etcdLeaseId);
    }

    private void createLeaseId(EtcdLeaseId etcdLeaseId) {
        logger.info("renewal of contract. server url {}", etcdLeaseId.getUrl());
        Client client = etcdLeaseId.getClientWrapper();
        try {
            long ttl = client.getLeaseClient().timeToLive(etcdLeaseId.getLeaseId(), LeaseOption.DEFAULT).get().getTTl();
            if (ttl < 1) {
                long leaseId = client.getLeaseClient().grant(1L).get().getID();
                client.getLeaseClient().keepAlive(leaseId, etcdLeaseId.getEtcdStreamObserver());
                etcdLeaseId.setLeaseId(leaseId);
            }
        } catch (Throwable e) {
            logger.error("renewal error", e);
            client.getLeaseClient().keepAlive(System.currentTimeMillis(), etcdLeaseId.getEtcdStreamObserver());
        }
    }

    public void setEtcdLeaseId(EtcdLeaseId etcdLeaseId) {
        this.etcdLeaseId = etcdLeaseId;
    }
}
