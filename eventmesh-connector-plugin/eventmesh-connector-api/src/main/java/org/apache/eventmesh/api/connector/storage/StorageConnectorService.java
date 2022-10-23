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

package org.apache.eventmesh.api.connector.storage;

import org.apache.eventmesh.api.LifeCycle;
import org.apache.eventmesh.api.connector.storage.data.PullRequest;
import org.apache.eventmesh.api.connector.storage.metadata.StorageMetaServcie;
import org.apache.eventmesh.api.connector.storage.pull.StoragePullService;
import org.apache.eventmesh.api.connector.storage.reply.ReplyOperationService;
import org.apache.eventmesh.spi.EventMeshExtensionFactory;

import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import lombok.Getter;

public class StorageConnectorService implements LifeCycle {

    private static final StorageConnectorService instance = new StorageConnectorService();

    private StoragePullService pullService = new StoragePullService();

    private StorageMetaServcie storageMetaServcie = new StorageMetaServcie();

    private ReplyOperationService replyService = new ReplyOperationService();

    private Map<String, StorageConnector> storageConnectorMap = new HashMap<>();

    private Executor executor;

    private ScheduledExecutorService scheduledExecutor;

    @Getter
    private StorageConnector storageConnector = new StorageConnectorProxy();

    public static StorageConnectorService getInstance() {
        return instance;
    }

    private StorageConnectorService() {
        this.executor = new ThreadPoolExecutor(Runtime.getRuntime().availableProcessors() * 10,
            Runtime.getRuntime().availableProcessors() * 100, 1000 * 60 * 60, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(), new ThreadFactory() {
            AtomicInteger index = new AtomicInteger();

            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, "storage-connent-" + index.getAndIncrement());
            }
        });
        this.scheduledExecutor = Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors() * 10,
            new ThreadFactory() {
                AtomicInteger index = new AtomicInteger();

                @Override
                public Thread newThread(Runnable r) {
                    return new Thread(r, "storage-connent-shceduled-" + index.getAndIncrement());
                }
            });
        this.storageMetaServcie = new StorageMetaServcie();
        this.storageMetaServcie.setScheduledExecutor(scheduledExecutor);
        this.storageMetaServcie.setStoragePullService(pullService);
        this.replyService.setExecutor(executor);
        this.pullService.setExecutor(executor);
        this.executor.execute(pullService);
        this.scheduled();

    }

    public void scheduled() {
        scheduledExecutor.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                storageMetaServcie.pullMeteData();
            }
        }, 5, 1000, TimeUnit.MILLISECONDS);
        scheduledExecutor.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                replyService.execute();
            }
        }, 5, 5, TimeUnit.MILLISECONDS);
    }

    public StorageConnector createConsumerByStorageConnector(Properties properties) {
        StorageConnector storageConnector = this.createConsumerByStorageConnector(properties);
        if (storageConnector instanceof StorageConnectorMetedata) {
            this.storageMetaServcie.registerStorageConnector((StorageConnectorMetedata) storageConnector);
        }

        return storageConnector;
    }

    public StorageConnector createProducerByStorageConnector(Properties properties, List<PullRequest> pullRequests) {
        StorageConnector storageConnector = this.createConsumerByStorageConnector(properties);
        this.storageMetaServcie.registerPullRequest(pullRequests, storageConnector);
        return storageConnector;
    }

    public StorageConnector createStorageConnector(Properties properties) throws Exception {
        URL url = new URL(properties.getProperty(""));
        String host = url.getHost() + ":" + url.getPort();
        String[] hosts = host.split(",");
        StorageConnectorProxy connectorProxy = new StorageConnectorProxy();
        for (String address : hosts) {
            StorageConnector storageConnector = EventMeshExtensionFactory.getExtension(StorageConnector.class,
                url.getProtocol());
            properties.setProperty("nodeAddress", address);
            properties.setProperty("protocol", url.getProtocol());
            storageConnector.init(properties);
            String key = url.getProtocol() + "://" + address;
            connectorProxy.setConnector(storageConnector, key);
            storageConnectorMap.put(key, storageConnector);
        }

        return connectorProxy;
    }

    @Override
    public boolean isStarted() {
        return true;
    }

    @Override
    public boolean isClosed() {
        return false;
    }

    @Override
    public void start() {
    }

    @Override
    public void shutdown() {
        storageConnectorMap.values().forEach(value -> value.shutdown());
    }
}
