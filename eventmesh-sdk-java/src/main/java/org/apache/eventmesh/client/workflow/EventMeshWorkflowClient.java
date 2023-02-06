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

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EventMeshWorkflowClient {

    private final transient EventMeshWorkflowClientConfig clientConfig;

    public EventMeshWorkflowClient(final EventMeshWorkflowClientConfig clientConfig) {
        this.clientConfig = clientConfig;
    }

    public WorkflowGrpc.WorkflowBlockingStub getWorkflowClient() throws Exception {
        final Selector selector = SelectorFactory.get(clientConfig.getSelectorType());
        if (selector == null) {
            throw new Exception(String.format("selector=%s not register.please check it.",
                    clientConfig.getSelectorType()));
        }
        final ServiceInstance instance = selector.selectOne(clientConfig.getServerName());
        if (instance == null) {
            throw new Exception("workflow server is not running.please check it.");
        }
        final ManagedChannel channel = ManagedChannelBuilder.forAddress(instance.getHost(),
                instance.getPort()).usePlaintext().build();
        return WorkflowGrpc.newBlockingStub(channel);
    }

    public ExecuteResponse execute(final ExecuteRequest request) throws Exception {
        final WorkflowGrpc.WorkflowBlockingStub workflowClient = getWorkflowClient();
        final ExecuteResponse response = workflowClient.execute(request);
        if (log.isInfoEnabled()) {
            log.info("received response:{}", response);
        }
        return response;
    }
}
