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

package org.apache.eventmesh.grpc.pub.eventmeshmessage;

import org.apache.eventmesh.client.grpc.producer.EventMeshGrpcProducer;
import org.apache.eventmesh.client.selector.SelectorFactory;
import org.apache.eventmesh.client.workflow.EventMeshWorkflowClient;
import org.apache.eventmesh.client.workflow.config.EventMeshWorkflowClientConfig;
import org.apache.eventmesh.common.ExampleConstants;
import org.apache.eventmesh.common.protocol.workflow.protos.ExecuteRequest;
import org.apache.eventmesh.common.protocol.workflow.protos.ExecuteResponse;
import org.apache.eventmesh.common.utils.ThreadUtils;
import org.apache.eventmesh.grpc.GrpcAbstractDemo;
import org.apache.eventmesh.selector.NacosSelector;
import org.apache.eventmesh.util.Utils;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import com.alibaba.nacos.shaded.com.google.gson.Gson;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WorkflowAsyncPublishInstance extends GrpcAbstractDemo {

    public static void main(String[] args) throws Exception {

        Properties properties = Utils.readPropertiesFile(ExampleConstants.CONFIG_FILE_NAME);
        final String workflowServerName = properties.getProperty(ExampleConstants.EVENTMESH_WORKFLOW_NAME);
        final String selectorType = properties.getProperty(ExampleConstants.EVENTMESH_SELECTOR_TYPE);

        try (EventMeshGrpcProducer eventMeshGrpcProducer = new EventMeshGrpcProducer(
            initEventMeshGrpcClientConfig(ExampleConstants.DEFAULT_EVENTMESH_TEST_PRODUCER_GROUP))) {

            NacosSelector nacosSelector = new NacosSelector();
            nacosSelector.init();
            SelectorFactory.register(selectorType, nacosSelector);

            ExecuteRequest.Builder executeRequest = ExecuteRequest.newBuilder();
            Map<String, String> content = new HashMap<>();
            content.put("order_no", "workflowmessage");
            executeRequest.setInput(new Gson().toJson(content));
            executeRequest.setId("testcreateworkflow");

            EventMeshWorkflowClientConfig eventMeshWorkflowClientConfig = EventMeshWorkflowClientConfig.builder()
                .serverName(workflowServerName).build();
            EventMeshWorkflowClient eventMeshWorkflowClient = new EventMeshWorkflowClient(eventMeshWorkflowClientConfig);
            ExecuteResponse response = eventMeshWorkflowClient.getWorkflowClient().execute(executeRequest.build());

            log.info("received response: {}", response.toString());

            ThreadUtils.sleep(1, TimeUnit.MINUTES);

        }
    }
}
