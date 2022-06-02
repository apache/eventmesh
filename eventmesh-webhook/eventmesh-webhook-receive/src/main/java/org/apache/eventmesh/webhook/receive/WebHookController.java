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

import java.net.URI;
import java.util.Map;
import java.util.UUID;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import org.apache.eventmesh.webhook.api.WebHookConfig;
import org.apache.eventmesh.webhook.receive.protocol.ProtocolManage;
import org.apache.eventmesh.webhook.receive.storage.HookConfigOperationManage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    public WebHookController() {
        this.hookConfigOperationManage = new HookConfigOperationManage();
    }


    /**
     * 1. get webhookConfig from path
     * 2. get ManufacturerProtocol and execute
     * 3. convert to cloudEvent obj
     *
     * @param path   CallbackPath
     * @param header map of webhook request header
     * @param body   data
     */
    public void execute(String path, Map<String, String> header, byte[] body) {
        // 1. get webhookConfig from path
        WebHookConfig webHookConfig = new WebHookConfig();
        webHookConfig.setCallbackPath(path);
        webHookConfig = hookConfigOperationManage.queryWebHookConfigById(webHookConfig);

        // 2. get ManufacturerProtocol and execute
        String manufacturerName = webHookConfig.getManufacturerName();
        ManufacturerProtocol protocol = protocolManage.getManufacturerProtocol(manufacturerName);
        WebHookRequest webHookRequest = new WebHookRequest();
        webHookRequest.setData(body);
        try {
            protocol.execute(webHookRequest, webHookConfig, header);
        } catch (Exception e) {
            logger.error("Webhook Message Parse Failed.");
            e.printStackTrace();
        }

        // 3. convert to cloudEvent obj
        String cloudEventId;
        if (webHookConfig.getCloudEventIdGenerateMode().equals("uuid")) {
            cloudEventId = UUID.randomUUID().toString();
        } else {
            cloudEventId = webHookRequest.getManufacturerEventId();
        }
        String eventType = manufacturerName + "." + webHookConfig.getManufacturerEventName();
        CloudEvent event = CloudEventBuilder.v1()
                .withId(cloudEventId)
                .withSubject(webHookConfig.getCloudEventName())
                .withSource(URI.create(webHookConfig.getCloudEventSource()))
                .withDataContentType(webHookConfig.getDataContentType())
                .withType(eventType)
                .withData(body)
                .build();
    }

}
