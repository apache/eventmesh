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

import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.api.SendResult;
import org.apache.eventmesh.api.exception.OnExceptionContext;
import org.apache.eventmesh.common.config.ConfigService;
import org.apache.eventmesh.common.protocol.ProtocolTransportObject;
import org.apache.eventmesh.common.protocol.http.WebhookProtocolTransportObject;
import org.apache.eventmesh.protocol.api.ProtocolAdaptor;
import org.apache.eventmesh.protocol.api.ProtocolPluginFactory;
import org.apache.eventmesh.webhook.api.WebHookConfig;
import org.apache.eventmesh.webhook.receive.config.ReceiveConfiguration;
import org.apache.eventmesh.webhook.receive.protocol.ProtocolManager;
import org.apache.eventmesh.webhook.receive.storage.HookConfigOperationManager;

import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.UUID;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WebHookController {

    private static final String PROTOCOL_ADAPTOR = "webhook";

    private static final String CONTENT_TYPE = "content-type";

    private static final String UUID_GENERATE_MODE = "uuid";

    private static final String DOT = ".";

    /**
     * protocol pool
     */
    private final transient ProtocolManager protocolManager = new ProtocolManager();

    /**
     * config pool
     */
    private transient HookConfigOperationManager hookConfigOperationManager;

    private transient WebHookMQProducer webHookMQProducer;

    private transient ProtocolAdaptor<ProtocolTransportObject> protocolAdaptor;

    private transient ReceiveConfiguration receiveConfiguration;

    public void init() throws Exception {
        receiveConfiguration = ConfigService.getInstance().buildConfigInstance(ReceiveConfiguration.class);
        Properties rootConfig = ConfigService.getInstance().getRootConfig();

        this.webHookMQProducer = new WebHookMQProducer(rootConfig, receiveConfiguration.getStoragePluginType());
        this.hookConfigOperationManager = new HookConfigOperationManager(receiveConfiguration);
        this.protocolAdaptor = ProtocolPluginFactory.getProtocolAdaptor(PROTOCOL_ADAPTOR);
    }

    /**
     * 1. get webhookConfig from path 2. get ManufacturerProtocol and execute 3. convert to cloudEvent obj 4. send cloudEvent
     *
     * @param path   CallbackPath
     * @param header map of webhook request header
     * @param body   data
     * @throws Exception if any uncaught exception occurs during execution
     */
    public void execute(String path, Map<String, String> header, byte[] body) throws Exception {

        // 1. get webhookConfig from path
        WebHookConfig webHookConfig = new WebHookConfig();
        webHookConfig.setCallbackPath(path);
        webHookConfig = hookConfigOperationManager.queryWebHookConfigById(webHookConfig);
        if (webHookConfig == null) {
            throw new Exception("No matching webhookConfig.");
        }

        if (!Objects.equals(webHookConfig.getContentType(), header.get(CONTENT_TYPE))) {
            throw new Exception(
                "http request header content-type value is mismatch. current value " + header.get(CONTENT_TYPE));
        }

        // 2. get ManufacturerProtocol and execute
        String manufacturerName = webHookConfig.getManufacturerName();
        ManufacturerProtocol protocol = protocolManager.getManufacturerProtocol(manufacturerName);
        WebHookRequest webHookRequest = new WebHookRequest();
        webHookRequest.setData(body);
        try {
            protocol.execute(webHookRequest, webHookConfig, header);
        } catch (Exception e) {
            throw new Exception("Webhook Message Parse Failed. " + e.getMessage(), e);
        }

        // 3. convert to cloudEvent obj
        String cloudEventId = UUID_GENERATE_MODE.equals(webHookConfig.getCloudEventIdGenerateMode()) ? UUID.randomUUID().toString()
            : webHookRequest.getManufacturerEventId();
        String eventType = manufacturerName + DOT + webHookConfig.getManufacturerEventName();

        WebhookProtocolTransportObject webhookProtocolTransportObject = WebhookProtocolTransportObject.builder()
            .cloudEventId(cloudEventId).eventType(eventType).cloudEventName(webHookConfig.getCloudEventName())
            .cloudEventSource(webHookConfig.getManufacturerDomain())
            .dataContentType(webHookConfig.getDataContentType()).body(body).build();

        // 4. send cloudEvent
        webHookMQProducer.send(this.protocolAdaptor.toCloudEvent(webhookProtocolTransportObject), new SendCallback() {

            @Override
            public void onSuccess(SendResult sendResult) {
                log.debug(sendResult.toString());
            }

            @Override
            public void onException(OnExceptionContext context) {
                log.warn("", context.getException());
            }

        });
    }

}
