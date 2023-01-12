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
import org.apache.eventmesh.webhook.receive.protocol.ProtocolManage;
import org.apache.eventmesh.webhook.receive.storage.HookConfigOperationManage;

import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WebHookController {

    private static final String PROTOCOL_ADAPTOR = "webhook";

    private static final String CONTENT_TYPE = "content-type";

    private static final String UUID_GENERATE_MODE = "uuid";

    private static final String DOT = ".";

    /**
     * protocol pool
     */
    private final ProtocolManage protocolManage = new ProtocolManage();

    public Logger logger = LoggerFactory.getLogger(this.getClass());

    /**
     * config pool
     */
    private HookConfigOperationManage hookConfigOperationManage;

    private WebHookMQProducer webHookMQProducer;

    private ProtocolAdaptor<ProtocolTransportObject> protocolAdaptor;

    private ReceiveConfiguration receiveConfiguration;

    public void init() throws Exception {
        receiveConfiguration = ConfigService.getInstance().getConfig(ReceiveConfiguration.class);
        Properties rootConfig = ConfigService.getInstance().getRootConfig();

        this.webHookMQProducer = new WebHookMQProducer(rootConfig, receiveConfiguration.getConnectorPluginType());
        this.hookConfigOperationManage = new HookConfigOperationManage(receiveConfiguration);
        this.protocolAdaptor = ProtocolPluginFactory.getProtocolAdaptor(PROTOCOL_ADAPTOR);
    }

    /**
     * 1. get webhookConfig from path 2. get ManufacturerProtocol and execute 3.
     * convert to cloudEvent obj 4. send cloudEvent
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

        if (!Objects.equals(webHookConfig.getContentType(), header.get(CONTENT_TYPE))) {
            throw new Exception(
                "http request header content-type value is mismatch. current value " + header.get(CONTENT_TYPE));
        }

        // 2. get ManufacturerProtocol and execute
        String manufacturerName = webHookConfig.getManufacturerName();
        ManufacturerProtocol protocol = protocolManage.getManufacturerProtocol(manufacturerName);
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
            .cloudEventSource("www." + webHookConfig.getManufacturerName() + ".com")
            .dataContentType(webHookConfig.getDataContentType()).body(body).build();

        // 4. send cloudEvent
        webHookMQProducer.send(this.protocolAdaptor.toCloudEvent(webhookProtocolTransportObject), new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                if (logger.isDebugEnabled()) {
                    logger.debug(sendResult.toString());
                }
            }

            @Override
            public void onException(OnExceptionContext context) {
                logger.warn("", context.getException());
            }

        });
    }

}
