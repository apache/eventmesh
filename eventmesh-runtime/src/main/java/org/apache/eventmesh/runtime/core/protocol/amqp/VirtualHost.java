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

package org.apache.eventmesh.runtime.core.protocol.amqp;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.UncheckedExecutionException;
import lombok.Getter;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * define virtual host in amqp
 * currently only support default vhost: "/"
 */
public class VirtualHost {
    @Getter
    private final String nameSpace;

    private static final LoadingCache<String, VirtualHost> cache;

    public static final VirtualHost DEFAULT_VHOST;

    private VirtualHost(String nameSpace) {
        this.nameSpace = nameSpace;
    }

    public static VirtualHost get(String nameSpace) {
        if (nameSpace != null && !nameSpace.isEmpty()) {
            try {
                return cache.get(nameSpace);
            } catch (ExecutionException | UncheckedExecutionException var2) {
                throw (RuntimeException) var2.getCause();
            }
        } else {
            throw new IllegalArgumentException("Invalid null namespace: " + nameSpace);
        }
    }

    static {
        cache = CacheBuilder.newBuilder()
                .maximumSize(100000L)
                .expireAfterAccess(30L, TimeUnit.MINUTES)
                .build(new CacheLoader<String, VirtualHost>() {
                    @Override
                    public VirtualHost load(String key) throws Exception {
                        return new VirtualHost(key);
                    }
                });
        DEFAULT_VHOST = get("/");
    }
}