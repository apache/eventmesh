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

import org.apache.eventmesh.api.exception.MetaException;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.meta.etcd.constant.EtcdConstant;

import org.apache.commons.lang3.StringUtils;

import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.ClientBuilder;
import io.etcd.jetcd.options.LeaseOption;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EtcdClientFactory {

    private static final Map<String, EtcdLeaseId> etcdLeaseIdMap = new ConcurrentHashMap<>();

    public static Client createClient(Properties properties) {
        String serverAddr = properties.getProperty(EtcdConstant.SERVER_ADDR);
        String username = properties.getProperty(EtcdConstant.USERNAME);
        String password = properties.getProperty(EtcdConstant.PASSWORD);

        EtcdLeaseId etcdLeaseId = etcdLeaseIdMap.get(serverAddr);
        if (Objects.nonNull(etcdLeaseId)) {
            return etcdLeaseId.getClientWrapper();
        }
        ClientBuilder clientBuilder = Client.builder();
        String[] addresses = serverAddr.split(",");
        String[] httpAddress = new String[addresses.length];
        for (int i = 0; i < addresses.length; i++) {
            if (!addresses[i].startsWith(Constants.HTTP_PROTOCOL_PREFIX)) {
                httpAddress[i] = Constants.HTTP_PROTOCOL_PREFIX + addresses[i];
            }
        }
        etcdLeaseId = new EtcdLeaseId();
        try {
            etcdLeaseId.setUrl(serverAddr);
            etcdLeaseId.setClientBuilder(clientBuilder.endpoints(httpAddress));
            if (StringUtils.isNoneBlank(username)) {
                etcdLeaseId.getClientBuilder().user(ByteSequence.from(username.getBytes(Constants.DEFAULT_CHARSET)));
            }
            if (StringUtils.isNoneBlank(password)) {
                etcdLeaseId.getClientBuilder().password(ByteSequence.from(password.getBytes(Constants.DEFAULT_CHARSET)));
            }
            etcdLeaseId.setClientWrapper(new EtcdClientWrapper(etcdLeaseId.getClientBuilder().build()));
            EtcdClientWrapper client = etcdLeaseId.getClientWrapper();
            long leaseId = client.getLeaseClient().grant(EtcdConstant.TTL).get().getID();
            etcdLeaseId.setLeaseId(leaseId);
            EtcdStreamObserver etcdStreamObserver = new EtcdStreamObserver();
            etcdStreamObserver.setEtcdLeaseId(etcdLeaseId);
            etcdLeaseId.setEtcdStreamObserver(etcdStreamObserver);
            client.getLeaseClient().keepAlive(leaseId, etcdStreamObserver);

            etcdLeaseIdMap.put(serverAddr, etcdLeaseId);
        } catch (Throwable e) {
            log.error("createClient failed, address: {}", serverAddr, e);
            throw new MetaException("createClient failed", e);
        }
        return etcdLeaseId.getClientWrapper();
    }

    public static void renewalLeaseId(EtcdLeaseId etcdLeaseId) {
        log.info("renewal of contract. server url: {}", etcdLeaseId.getUrl());
        Client client = etcdLeaseId.getClientWrapper();
        try {
            long ttl = client.getLeaseClient().timeToLive(etcdLeaseId.getLeaseId(), LeaseOption.DEFAULT).get().getTTl();
            if (ttl < 1) {
                long leaseId = client.getLeaseClient().grant(EtcdConstant.TTL).get().getID();
                client.getLeaseClient().keepAlive(leaseId, etcdLeaseId.getEtcdStreamObserver());
                etcdLeaseId.setLeaseId(leaseId);
            }
        } catch (Throwable e) {
            log.error("renewal error, server url: {}", etcdLeaseId.getUrl(), e);
            client.getLeaseClient().keepAlive(System.currentTimeMillis(), etcdLeaseId.getEtcdStreamObserver());
        }
    }

    public static Long getLeaseId(String url) {
        return getEtcdLeaseId(url).getLeaseId();
    }

    public static EtcdLeaseId getEtcdLeaseId(String url) {
        return etcdLeaseIdMap.get(url);
    }

}
