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
import org.apache.eventmesh.client.selector.Selector;
import org.apache.eventmesh.client.selector.SelectorFactory;
import org.apache.eventmesh.client.selector.ServiceInstance;
import org.apache.eventmesh.common.protocol.catalog.protos.CatalogGrpc;
import org.apache.eventmesh.common.protocol.catalog.protos.Operation;
import org.apache.eventmesh.common.protocol.catalog.protos.QueryOperationsRequest;
import org.apache.eventmesh.common.protocol.catalog.protos.QueryOperationsResponse;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

public class EventMeshCatalogClient {
    private static final Logger logger = LoggerFactory.getLogger(EventMeshCatalogClient.class);
    private final EventMeshCatalogClientConfig clientConfig;

    private List<Operation> operations = new ArrayList<>();

    public EventMeshCatalogClient(EventMeshCatalogClientConfig clientConfig) {
        this.clientConfig = clientConfig;
    }

    public void init() throws Exception {
        Selector selector = SelectorFactory.get(clientConfig.getSelectorType());
        ServiceInstance instance = selector.selectOne(clientConfig.getServerName());
        if (instance == null) {
            throw new Exception("catalog server is not running.please check it.");
        }
        ManagedChannel channel = ManagedChannelBuilder.forAddress(instance.getHost(), instance.getPort())
            .usePlaintext().build();
        CatalogGrpc.CatalogBlockingStub catalogClient = CatalogGrpc.newBlockingStub(channel);
        QueryOperationsRequest request = QueryOperationsRequest.newBuilder().setServiceName(clientConfig.getAppServerName()).build();
        try {
            QueryOperationsResponse response = catalogClient.queryOperations(request);
            logger.info("received response " + response.toString());
            operations = response.getOperationsList();
        } catch (Exception e) {
            logger.error("queryOperations error {}", e.getMessage());
            throw e;
        }
    }

    public List<Operation> getOperations() {
        return operations;
    }
}
