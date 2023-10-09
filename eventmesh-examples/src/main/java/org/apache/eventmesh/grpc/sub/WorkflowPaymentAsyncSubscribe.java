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

package org.apache.eventmesh.grpc.sub;

import org.apache.eventmesh.client.catalog.EventMeshCatalogClient;
import org.apache.eventmesh.client.catalog.config.EventMeshCatalogClientConfig;
import org.apache.eventmesh.client.grpc.consumer.EventMeshGrpcConsumer;
import org.apache.eventmesh.client.grpc.consumer.ReceiveMsgHook;
import org.apache.eventmesh.client.selector.SelectorFactory;
import org.apache.eventmesh.client.workflow.EventMeshWorkflowClient;
import org.apache.eventmesh.client.workflow.config.EventMeshWorkflowClientConfig;
import org.apache.eventmesh.common.EventMeshMessage;
import org.apache.eventmesh.common.ExampleConstants;
import org.apache.eventmesh.common.enums.EventMeshMessageProtocolType;
import org.apache.eventmesh.common.protocol.workflow.protos.ExecuteRequest;
import org.apache.eventmesh.common.protocol.workflow.protos.ExecuteResponse;
import org.apache.eventmesh.common.utils.ThreadUtils;
import org.apache.eventmesh.grpc.GrpcAbstractDemo;
import org.apache.eventmesh.selector.NacosSelector;
import org.apache.eventmesh.util.Utils;

import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WorkflowPaymentAsyncSubscribe extends GrpcAbstractDemo implements ReceiveMsgHook<EventMeshMessage> {

    private static EventMeshWorkflowClient workflowClient;

    public static void main(String[] args) throws Exception {
        Properties properties = Utils.readPropertiesFile(ExampleConstants.CONFIG_FILE_NAME);
        final String serverName = "paymentapp";
        final String workflowServerName = properties.getProperty(ExampleConstants.EVENTMESH_WORKFLOW_NAME);
        final String catalogServerName = properties.getProperty(ExampleConstants.EVENTMESH_CATALOG_NAME);
        final String selectorType = properties.getProperty(ExampleConstants.EVENTMESH_SELECTOR_TYPE);

        try (EventMeshGrpcConsumer eventMeshGrpcConsumer = new EventMeshGrpcConsumer(
            initEventMeshGrpcClientConfig(ExampleConstants.DEFAULT_EVENTMESH_TEST_CONSUMER_GROUP))) {
            eventMeshGrpcConsumer.init();
            eventMeshGrpcConsumer.registerListener(new WorkflowPaymentAsyncSubscribe());

            final NacosSelector nacosSelector = new NacosSelector();
            nacosSelector.init();
            SelectorFactory.register(selectorType, nacosSelector);

            final EventMeshCatalogClientConfig eventMeshCatalogClientConfig = EventMeshCatalogClientConfig.builder()
                .serverName(catalogServerName)
                .appServerName(serverName).build();
            final EventMeshCatalogClient eventMeshCatalogClient = new EventMeshCatalogClient(eventMeshCatalogClientConfig,
                eventMeshGrpcConsumer);
            eventMeshCatalogClient.init();

            final EventMeshWorkflowClientConfig eventMeshWorkflowClientConfig = EventMeshWorkflowClientConfig.builder()
                .serverName(workflowServerName).build();
            workflowClient = new EventMeshWorkflowClient(eventMeshWorkflowClientConfig);

            ThreadUtils.sleep(60_000, TimeUnit.SECONDS);
            eventMeshCatalogClient.destroy();
        }
    }

    @Override
    public Optional<EventMeshMessage> handle(final EventMeshMessage msg) throws Exception {
        if (log.isInfoEnabled()) {
            log.info("receive async msg: {}", msg);
        }
        if (msg == null) {
            log.info("async msg is null, workflow end.");
            return Optional.empty();
        }

        final Map<String, String> props = msg.getProp();
        final String workflowInstanceId = props.get("workflowinstanceid");
        final String taskInstanceId = props.get("workflowtaskinstanceid");

        final ExecuteRequest executeRequest = ExecuteRequest.newBuilder().setId("testcreateworkflow")
            .setTaskInstanceId(taskInstanceId)
            .setInstanceId(workflowInstanceId).build();
        final ExecuteResponse response = workflowClient.getWorkflowClient().execute(executeRequest);
        if (log.isInfoEnabled()) {
            log.info("receive workflow msg: {}", response.getInstanceId());
        }
        return Optional.empty();
    }

    @Override
    public EventMeshMessageProtocolType getProtocolType() {
        return EventMeshMessageProtocolType.EVENT_MESH_MESSAGE;
    }
}
