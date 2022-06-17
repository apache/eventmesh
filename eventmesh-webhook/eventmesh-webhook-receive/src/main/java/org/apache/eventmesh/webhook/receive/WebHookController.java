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

package org.apache.eventmesh.webhook.receive;

import org.apache.eventmesh.client.http.conf.EventMeshHttpClientConfig;
import org.apache.eventmesh.client.http.producer.EventMeshHttpProducer;
import org.apache.eventmesh.common.utils.ThreadUtils;
import org.apache.eventmesh.webhook.api.WebHookConfig;
import org.apache.eventmesh.webhook.receive.protocol.ProtocolManage;
import org.apache.eventmesh.webhook.receive.storage.HookConfigOperationManage;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

import com.alibaba.nacos.api.exception.NacosException;

public class WebHookController {

    public Logger logger = LoggerFactory.getLogger(this.getClass());

    /**
     * protocol pool
     */
    private final ProtocolManage protocolManage = new ProtocolManage();

    /**
     * config pool
     */
    private final HookConfigOperationManage hookConfigOperationManage;

    private Properties configProperties;

    public WebHookController() throws IOException, NacosException {
        readConfigFromConfigFile();

        this.hookConfigOperationManage =
            new HookConfigOperationManage(configProperties.getProperty("eventMesh.webHook.operationMode"),
                configProperties.getProperty("eventMesh.webHook.fileMode.filePath"));
    }

    /**
     * Read webHook related configurations from the global configuration file
     */
    public void readConfigFromConfigFile() throws IOException {
        try (final InputStream inputStream =
                 WebHookController.class.getClassLoader().getResourceAsStream("eventmesh.properties")) {
            configProperties = new Properties();
            configProperties.load(inputStream);
        }
    }

    /**
     * 1. get webhookConfig from path
     * 2. get ManufacturerProtocol and execute
     * 3. convert to cloudEvent obj
     * 4. send cloudEvent
     *
     * @param path   CallbackPath
     * @param header map of webhook request header
     * @param body   data
     */
    public void execute(String path, Map<String, String> header, byte[] body) throws Exception {
        // 1. get webhookConfig from path
        WebHookConfig webHookConfig = new WebHookConfig();
        webHookConfig.setCallbackPath(path);
        webHookConfig = hookConfigOperationManage.queryWebHookConfigById(webHookConfig);
        if (webHookConfig == null) {
            throw new Exception("No matching webhookConfig.");
        }

        // 2. get ManufacturerProtocol and execute
        String manufacturerName = webHookConfig.getManufacturerName();
        ManufacturerProtocol protocol = protocolManage.getManufacturerProtocol(manufacturerName);
        WebHookRequest webHookRequest = new WebHookRequest();
        webHookRequest.setData(body);
        try {
            protocol.execute(webHookRequest, webHookConfig, header);
        } catch (Exception e) {
            throw new Exception("Webhook Message Parse Failed.");
        }

        // 3. convert to cloudEvent obj
        String cloudEventId = "uuid".equals(webHookConfig.getCloudEventIdGenerateMode())
            ? UUID.randomUUID().toString() : webHookRequest.getManufacturerEventId();
        String eventType = manufacturerName + "." + webHookConfig.getManufacturerEventName();

        CloudEvent event = CloudEventBuilder.v1()
            .withId(cloudEventId)
            .withSubject(webHookConfig.getCloudEventName())
            .withSource(URI.create(webHookConfig.getCloudEventSource()))
            .withDataContentType(webHookConfig.getDataContentType())
            .withType(eventType)
            .withData(body)
            .build();


        // 4. send cloudEvent

        String eventMeshIPPort = configProperties.getProperty("eventMesh.server.ip")
            + ":" + configProperties.getProperty("eventMesh.server.http.port");

        // Both the producer and consumer require an instance of EventMeshHttpClientConfig class
        // that specifies the configuration of EventMesh HTTP client.
        EventMeshHttpClientConfig eventMeshClientConfig = EventMeshHttpClientConfig.builder()
            .liteEventMeshAddr(eventMeshIPPort)
            .producerGroup("eventMesh.webhook.producerGroup")
            .env(configProperties.getProperty("eventMesh.server.env"))
            .idc(configProperties.getProperty("eventMesh.server.idc"))
            .ip(configProperties.getProperty("eventMesh.server.ip"))
            .sys(configProperties.getProperty("eventMesh.sysid"))
            .pid(String.valueOf(ThreadUtils.getPID()))
            .userName(configProperties.getProperty("eventMesh.server.http.username"))
            .password(configProperties.getProperty("eventMesh.server.http.password"))
            .build();

        try (EventMeshHttpProducer eventMeshHttpProducer = new EventMeshHttpProducer(eventMeshClientConfig)) {
            eventMeshHttpProducer.publish(event);
        }
    }

}
