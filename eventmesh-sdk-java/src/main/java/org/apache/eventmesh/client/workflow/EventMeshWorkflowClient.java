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

package org.apache.eventmesh.client.workflow;

import org.apache.eventmesh.client.selector.Selector;
import org.apache.eventmesh.client.selector.SelectorFactory;
import org.apache.eventmesh.client.selector.ServiceInstance;
import org.apache.eventmesh.client.workflow.config.EventMeshWorkflowClientConfig;
import org.apache.eventmesh.common.protocol.workflow.protos.ExecuteRequest;
import org.apache.eventmesh.common.protocol.workflow.protos.ExecuteResponse;
import org.apache.eventmesh.common.protocol.workflow.protos.WorkflowGrpc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

public class EventMeshWorkflowClient {

    private static final Logger logger = LoggerFactory.getLogger(EventMeshWorkflowClient.class);
    private final EventMeshWorkflowClientConfig clientConfig;

    public EventMeshWorkflowClient(EventMeshWorkflowClientConfig clientConfig) {
        this.clientConfig = clientConfig;
    }

    public WorkflowGrpc.WorkflowBlockingStub getWorkflowClient() throws Exception {
        Selector selector = SelectorFactory.get(clientConfig.getSelectorType());
        ServiceInstance instance = selector.selectOne(clientConfig.getServerName());
        if (instance == null) {
            throw new Exception("workflow server is not running.please check it.");
        }
        ManagedChannel channel = ManagedChannelBuilder.forAddress(instance.getHost(), instance.getPort())
            .usePlaintext().build();
        return WorkflowGrpc.newBlockingStub(channel);
    }

    public ExecuteResponse execute(ExecuteRequest request) {
        try {
            WorkflowGrpc.WorkflowBlockingStub workflowClient = getWorkflowClient();
            ExecuteResponse response = workflowClient.execute(request);
            logger.info("received response " + response.toString());
            return response;
        } catch (Exception e) {
            logger.error("execute error {}", e.getMessage());
            return null;
        }
    }
}
