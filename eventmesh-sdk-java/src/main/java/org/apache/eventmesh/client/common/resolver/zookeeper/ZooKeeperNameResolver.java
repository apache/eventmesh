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

package org.apache.eventmesh.client.common.resolver.zookeeper;


import org.apache.eventmesh.client.common.EventMeshAddress;
import org.apache.eventmesh.client.common.constant.ZooKeeperConstant;
import org.apache.eventmesh.client.common.resolver.EventMeshNameResolver;
import org.apache.eventmesh.client.common.resolver.ServerListener;
import org.apache.eventmesh.client.common.util.URIUtils;

import org.apache.commons.lang3.StringUtils;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.cache.ChildData;
import org.apache.curator.framework.recipes.cache.TreeCache;
import org.apache.curator.framework.recipes.cache.TreeCacheEvent;
import org.apache.curator.retry.RetryNTimes;

import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

public class ZooKeeperNameResolver implements EventMeshNameResolver<EventMeshAddress> {


    private static final Logger logger = LoggerFactory.getLogger(ZooKeeperNameResolver.class);


    private static final String DEFAULT_SESSION_TIMEOUT_MS = "5000";
    private static final String DEFAULT_CONNECTION_TIMEOUT_MS = "5000";

    private ServerListener listener;

    private URI zookeeperUri;

    private CuratorFramework client;

    private Set<EventMeshAddress> serverInstanceSet = Sets.newConcurrentHashSet();

    private TreeCache treeCache;

    //ensure treeCache initialization is complete
    private Semaphore semaphore;


    @Override
    public void init(URI target) {
        this.zookeeperUri = target;

        Map<String, String> params = URIUtils.getParams(this.zookeeperUri);

        String sessionTimeoutMs = params.getOrDefault(
            ZooKeeperConstant.SESSION_TIMEOUT_MS,
            DEFAULT_SESSION_TIMEOUT_MS);

        String connectionTimeoutMs = params.getOrDefault(
            ZooKeeperConstant.CONNECTION_TIMEOUT_MS,
            DEFAULT_CONNECTION_TIMEOUT_MS);

        String sleepMsBetweenRetries = params.getOrDefault(
            ZooKeeperConstant.SLEEP_MS_BETWEEN_RETRIES,
            DEFAULT_CONNECTION_TIMEOUT_MS);


        this.client = CuratorFrameworkFactory.builder()
            .namespace(ZooKeeperConstant.NAME_SPACE)
            .connectString(this.zookeeperUri.getAuthority())
            .sessionTimeoutMs(Integer.parseInt(sessionTimeoutMs))
            .connectionTimeoutMs(Integer.parseInt(connectionTimeoutMs))
            .retryPolicy(new RetryNTimes(Integer.MAX_VALUE, Integer.parseInt(sleepMsBetweenRetries)))
            .build();
    }

    @Override
    public void start(ServerListener serverListener) {
        this.listener = serverListener;

        this.client.start();

        this.createServerInstanceListener();

        try {
            this.treeCache.start();
            this.semaphore.tryAcquire(ZooKeeperConstant.INIT_TIMEOUT, TimeUnit.MILLISECONDS);

            Optional.ofNullable(this.treeCache.getCurrentChildren(ZooKeeperConstant.SEPARATOR))
                .ifPresent(clusterMap ->
                    this.serverInstanceSet = clusterMap.values().stream()
                        .flatMap(clusterData ->
                            Optional.ofNullable(this.treeCache.getCurrentChildren(clusterData.getPath()))
                                .orElseGet(() -> Maps.newHashMap())
                                .values().stream())
                        .flatMap(serverData ->
                            Optional.ofNullable(this.treeCache.getCurrentChildren(serverData.getPath()))
                                .orElseGet(() -> Maps.newHashMap())
                                .keySet().stream())
                        .map(serverInstance -> this.convert2Address(serverInstance))
                        .collect(Collectors.toSet())
                );

            this.listener.refresh(this.serverInstanceSet);
        } catch (Exception e) {
            throw new RuntimeException("ZooKeeperNameResolver.resolver failed", e);
        }
    }

    private void createServerInstanceListener() {

        this.treeCache = new TreeCache(this.client, ZooKeeperConstant.SEPARATOR);
        this.semaphore = new Semaphore(0);

        this.treeCache.getListenable().addListener((client, event) -> {

            if (event.getType() == TreeCacheEvent.Type.INITIALIZED) {
                this.semaphore.release();
            }

            final ChildData eventData = event.getData();
            if (eventData == null) {
                return;
            }

            // The service storage structure is     /clusterName/serverName/ip:port
            final String path = eventData.getPath();
            final String[] split = StringUtils.split(path, ZooKeeperConstant.SEPARATOR);
            if (split.length != ZooKeeperConstant.SPLIT_LENGTH) {
                return;
            }

            String serverInstance = split[split.length - 1];

            switch (event.getType()) {
                case NODE_ADDED:
                    logger.info("serverInstance {} is added", serverInstance);
                    this.serverInstanceSet.add(this.convert2Address(serverInstance));
                    this.listener.refresh(this.serverInstanceSet);
                    break;
                case NODE_REMOVED:
                    logger.info("serverInstance {} is removed", serverInstance);
                    this.serverInstanceSet.remove(this.convert2Address(serverInstance));
                    this.listener.refresh(this.serverInstanceSet);
                    break;
                default:
                    break;
            }

        });
    }

    @Override
    public void shutdown() {

        this.treeCache.close();

        this.client.close();
    }


    private EventMeshAddress convert2Address(String serverInstance) {
        final String[] ipPort = serverInstance.split(":");

        final EventMeshAddress address = new EventMeshAddress();
        address.setHost(ipPort[0]);
        address.setPort(Integer.valueOf(ipPort[1]));

        return address;
    }
}
