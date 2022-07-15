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

package org.apache.eventmesh.client.extension.resolver.zookeeper;


import org.apache.eventmesh.client.extension.EventMeshNameResolver;
import org.apache.eventmesh.client.extension.constant.ZooKeeperConstant;
import org.apache.eventmesh.client.extension.resolver.NameResolverListener;
import org.apache.eventmesh.client.extension.util.URIUtils;

import org.apache.commons.lang3.StringUtils;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.cache.ChildData;
import org.apache.curator.framework.recipes.cache.TreeCache;
import org.apache.curator.retry.RetryNTimes;

import java.net.URI;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Sets;

public class ZooKeeperNameResolver implements EventMeshNameResolver {

    private static final Logger logger = LoggerFactory.getLogger(ZooKeeperNameResolver.class);


    private static final String DEFAULT_SESSION_TIMEOUT_MS = "5000";
    private static final String DEFAULT_CONNECTION_TIMEOUT_MS = "";

    private NameResolverListener listener;

    private URI zookeeperUri;

    private CuratorFramework client;

    private Set<String> serverInstanceSet = Sets.newConcurrentHashSet();

    public ZooKeeperNameResolver(URI uri, NameResolverListener listener) {
        this.zookeeperUri = uri;
        this.listener = listener;

        Map<String, String> params = URIUtils.getParams(zookeeperUri);

        String sessionTimeoutMs = params.getOrDefault(
            ZooKeeperConstant.SESSION_TIMEOUT_MS,
            DEFAULT_SESSION_TIMEOUT_MS);

        String connectionTimeoutMs = params.getOrDefault(
            ZooKeeperConstant.CONNECTION_TIMEOUT_MS,
            DEFAULT_CONNECTION_TIMEOUT_MS);

        String sleepMsBetweenRetries = params.getOrDefault(
            ZooKeeperConstant.SLEEP_MS_BETWEEN_RETRIES,
            DEFAULT_CONNECTION_TIMEOUT_MS);


        client = CuratorFrameworkFactory.builder()
            .namespace(ZooKeeperConstant.NAME_SPACE)
            .connectString(zookeeperUri.getAuthority())
            .sessionTimeoutMs(Integer.parseInt(sessionTimeoutMs))
            .connectionTimeoutMs(Integer.parseInt(connectionTimeoutMs))
            .retryPolicy(new RetryNTimes(Integer.MAX_VALUE, Integer.parseInt(sleepMsBetweenRetries)))
            .build();
        client.start();

        createServerListener();

    }

    private void createServerListener() {
        try {
            final TreeCache treeCache = new TreeCache(client, ZooKeeperConstant.SEPARATOR);

            treeCache.getListenable().addListener((client, event) -> {
                final ChildData eventData = event.getData();
                final String path = eventData.getPath();

                // The service storage structure is     /clusterName/serverName/ip:port
                final String[] split = StringUtils.split(path, ZooKeeperConstant.SEPARATOR);
                if (split.length != ZooKeeperConstant.SPLIT_LENGTH) {
                    return;
                }

                String serverInstance = split[split.length - 1];

                switch (event.getType()) {
                    case NODE_ADDED:
                        serverInstanceSet.add(serverInstance);
                        listener.refresh(serverInstanceSet);
                        break;
                    case NODE_REMOVED:
                        serverInstanceSet.remove(serverInstance);
                        listener.refresh(serverInstanceSet);
                        break;
                    default:
                        break;
                }

            });
            treeCache.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public Set<String> resolver() {
        return serverInstanceSet;
    }
}
