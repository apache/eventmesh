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
import org.apache.eventmesh.client.grpc.config.EventMeshGrpcClientConfig;
import org.apache.eventmesh.client.grpc.consumer.EventMeshGrpcConsumer;
import org.apache.eventmesh.client.grpc.consumer.ReceiveMsgHook;
import org.apache.eventmesh.client.selector.SelectorFactory;
import org.apache.eventmesh.client.tcp.common.EventMeshCommon;
import org.apache.eventmesh.client.workflow.EventMeshWorkflowClient;
import org.apache.eventmesh.client.workflow.config.EventMeshWorkflowClientConfig;
import org.apache.eventmesh.common.EventMeshMessage;
import org.apache.eventmesh.common.ExampleConstants;
import org.apache.eventmesh.common.protocol.workflow.protos.ExecuteRequest;
import org.apache.eventmesh.common.protocol.workflow.protos.ExecuteResponse;
import org.apache.eventmesh.selector.NacosSelector;
import org.apache.eventmesh.util.Utils;

import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WorkflowOrderAsyncSubscribe implements ReceiveMsgHook<EventMeshMessage> {

    public static WorkflowOrderAsyncSubscribe handler = new WorkflowOrderAsyncSubscribe();
    public static EventMeshWorkflowClient workflowClient;

    public static void main(String[] args) throws Exception {
        Properties properties = Utils.readPropertiesFile(ExampleConstants.CONFIG_FILE_NAME);
        final String eventMeshIp = properties.getProperty(ExampleConstants.EVENTMESH_IP);
        final String eventMeshGrpcPort = properties.getProperty(ExampleConstants.EVENTMESH_GRPC_PORT);
        final String serverName = "order";
        final String workflowServerName = properties.getProperty(ExampleConstants.EVENTMESH_WORKFLOW_NAME);
        final String catalogServerName = properties.getProperty(ExampleConstants.EVENTMESH_CATALOG_NAME);
        final String selectorType = properties.getProperty(ExampleConstants.EVENTMESH_SELECTOR_TYPE);

        EventMeshGrpcClientConfig eventMeshClientConfig = EventMeshGrpcClientConfig.builder()
            .serverAddr(eventMeshIp)
            .serverPort(Integer.parseInt(eventMeshGrpcPort))
            .consumerGroup(ExampleConstants.DEFAULT_EVENTMESH_TEST_CONSUMER_GROUP)
            .env("test").idc("default").password("password")
            .sys("default").build();

        EventMeshGrpcConsumer eventMeshGrpcConsumer = new EventMeshGrpcConsumer(eventMeshClientConfig);
        eventMeshGrpcConsumer.init();
        eventMeshGrpcConsumer.registerListener(handler);

        NacosSelector nacosSelector = new NacosSelector();
        nacosSelector.init();
        SelectorFactory.register(selectorType, nacosSelector);

        EventMeshCatalogClientConfig eventMeshCatalogClientConfig = EventMeshCatalogClientConfig.builder().serverName(catalogServerName)
            .appServerName(serverName).build();
        EventMeshCatalogClient eventMeshCatalogClient = new EventMeshCatalogClient(eventMeshCatalogClientConfig, eventMeshGrpcConsumer);
        eventMeshCatalogClient.init();

        EventMeshWorkflowClientConfig eventMeshWorkflowClientConfig = EventMeshWorkflowClientConfig.builder().serverName(workflowServerName).build();
        workflowClient = new EventMeshWorkflowClient(eventMeshWorkflowClientConfig);

        Thread.sleep(6000000);
        eventMeshCatalogClient.destroy();
    }

    @Override
    public Optional<EventMeshMessage> handle(EventMeshMessage msg) throws Exception {
        log.info("receive async msg: {}", msg);

        Map<String, String> props = msg.getProp();
        String workflowInstanceId = props.get("workflowinstanceid");
        String taskInstanceId = props.get("workflowtaskinstanceid");

        ExecuteRequest executeRequest = ExecuteRequest.newBuilder().setId("storeorderworkflow")
            .setInstanceId(workflowInstanceId).build();
        ExecuteResponse response = workflowClient.getWorkflowClient().execute(executeRequest);
        log.info("receive workflow msg: {}", response.getInstanceId());
        return Optional.empty();
    }

    @Override
    public String getProtocolType() {
        return EventMeshCommon.EM_MESSAGE_PROTOCOL_NAME;
    }
}
