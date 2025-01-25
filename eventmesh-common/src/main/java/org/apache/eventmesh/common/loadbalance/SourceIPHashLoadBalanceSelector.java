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

package org.apache.eventmesh.common.loadbalance;

import org.apache.commons.lang3.StringUtils;

import java.util.Collections;
import java.util.List;


import lombok.extern.slf4j.Slf4j;

/**
 * Source IP Hash LoadBalance: make the same client always accessing the same server.
 *
 * @param <T> Target type
 */
@Slf4j
public class SourceIPHashLoadBalanceSelector<T> implements LoadBalanceSelector<T> {

    private final transient List<T> servers;

    private String clientKey;

    public SourceIPHashLoadBalanceSelector(List<T> servers, String clientKey) {
        this.servers = servers;
        this.clientKey = clientKey;
    }

    @Override
    public T select() {
        // Avoid servers being changed during select().
        List<T> targets = Collections.unmodifiableList(servers);
        if (StringUtils.isBlank(clientKey)) {
            clientKey = "127.0.0.1";
            log.warn("Blank client IP has been set default {}", clientKey);
        }
        int hashCode = hash(clientKey);
        int index = hashCode % targets.size();
        return targets.get(index);
    }

    @Override
    public LoadBalanceType getType() {
        return LoadBalanceType.SOURCE_IP_HASH;
    }

    /**
     * FNV hash algorithm that is suitable for hashing some similar strings, like IP.
     * @return
     */
    private int hash(String data) {
        final int p = 16777619;
        int hash = (int) 2166136261L;
        for (int i = 0; i < data.length(); i++) {
            hash = (hash ^ data.charAt(i)) * p;
        }
        hash += hash << 13;
        hash ^= hash >> 7;
        hash += hash << 3;
        hash ^= hash >> 17;
        hash += hash << 5;
        if (hash < 0) {
            hash = Math.abs(hash);
        }
        return hash;
    }
}
