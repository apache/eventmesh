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

package org.apache.eventmesh.client.catalog;

import org.apache.eventmesh.client.catalog.config.EventMeshCatalogClientConfig;
import org.apache.eventmesh.client.grpc.consumer.EventMeshGrpcConsumer;
import org.apache.eventmesh.client.selector.Selector;
import org.apache.eventmesh.client.selector.SelectorFactory;
import org.apache.eventmesh.client.selector.ServiceInstance;
import org.apache.eventmesh.common.protocol.SubscriptionItem;
import org.apache.eventmesh.common.protocol.catalog.protos.CatalogGrpc;
import org.apache.eventmesh.common.protocol.catalog.protos.Operation;
import org.apache.eventmesh.common.protocol.catalog.protos.QueryOperationsRequest;
import org.apache.eventmesh.common.protocol.catalog.protos.QueryOperationsResponse;
import org.apache.eventmesh.common.utils.AssertUtils;

import org.apache.commons.collections4.CollectionUtils;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

public class EventMeshCatalogClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(EventMeshCatalogClient.class);
    private final transient EventMeshCatalogClientConfig clientConfig;
    private final transient EventMeshGrpcConsumer eventMeshGrpcConsumer;
    private final transient List<SubscriptionItem> subscriptionItems = new ArrayList<>();

    public EventMeshCatalogClient(EventMeshCatalogClientConfig clientConfig,
                                  EventMeshGrpcConsumer eventMeshGrpcConsumer) {
        this.clientConfig = clientConfig;
        this.eventMeshGrpcConsumer = eventMeshGrpcConsumer;
    }

    public void init() throws Exception {
        Selector selector = SelectorFactory.get(clientConfig.getSelectorType());
        AssertUtils.notNull(selector, String.format("selector=%s not register.please check it.",
                clientConfig.getSelectorType()));
        ServiceInstance instance = selector.selectOne(clientConfig.getServerName());
        AssertUtils.notNull(instance, "catalog server is not running.please check it.");

        ManagedChannel channel = ManagedChannelBuilder.forAddress(instance.getHost(), instance.getPort())
                .usePlaintext().build();
        CatalogGrpc.CatalogBlockingStub catalogClient = CatalogGrpc.newBlockingStub(channel);

        QueryOperationsRequest request = QueryOperationsRequest.newBuilder()
                .setServiceName(clientConfig.getAppServerName()).build();
        QueryOperationsResponse response = catalogClient.queryOperations(request);
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("received response: {}", response.toString());
        }

        List<Operation> operations = response.getOperationsList();
        if (CollectionUtils.isEmpty(operations)) {
            return;
        }

        for (Operation operation : operations) {
            if (!"subscribe".equals(operation.getType())) {
                continue;
            }
            SubscriptionItem subscriptionItem = new SubscriptionItem();
            subscriptionItem.setTopic(operation.getChannelName());
            subscriptionItem.setMode(clientConfig.getSubscriptionMode());
            subscriptionItem.setType(clientConfig.getSubscriptionType());
            subscriptionItems.add(subscriptionItem);
        }
        eventMeshGrpcConsumer.subscribe(subscriptionItems);
    }

    public void destroy() {
        if (subscriptionItems == null || subscriptionItems.isEmpty()) {
            return;
        }
        eventMeshGrpcConsumer.unsubscribe(subscriptionItems);
    }
}