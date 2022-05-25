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
     * 协议池
     */
    private ProtocolManage protocolManage = new ProtocolManage();

    /**
     * 配置池
     */
    private HookConfigOperationManage hookConfigOperationManage;

    public WebHookController() {
        this.hookConfigOperationManage = new HookConfigOperationManage();
    }


    /**
     * 1. 通过 path 获得 webhookConfig
     * 2. 获得对应的厂商的处理对象 , 并解析协议
     * 3. 通过 WebHookConfig 和 WebHookRequest 获得 cloudEvent 协议对象
     * @param path   CallbackPath
     * @param header 需要把请求头信息重写到 map 里面
     * @param body   data
     */
    public void execute(String path, Map<String, String> header, byte[] body) {
        // 1. 通过 path 获得 webhookConfig
        WebHookConfig webHookConfig = new WebHookConfig();
        webHookConfig.setCallbackPath(path);
        webHookConfig = hookConfigOperationManage.queryWebHookConfigById(webHookConfig);

        // 2. 获得对应的厂商的处理对象 , 并解析协议
        String manufacturerName = webHookConfig.getManufacturerName();
        ManufacturerProtocol protocol = protocolManage.getManufacturerProtocol(manufacturerName);
        WebHookRequest webHookRequest = new WebHookRequest();
        webHookRequest.setData(body);
        protocol.execute(webHookRequest, webHookConfig, header);

        // 3. 通过 WebHookConfig 和 WebHookRequest 获得 cloudEvent 协议对象
        String cloudEventId;
        if (webHookConfig.getCloudEventIdGenerateMode().equals("uuid")) {
            cloudEventId = UUID.randomUUID().toString();
        } else {
            cloudEventId = webHookRequest.getManufacturerEventId();
        }
        String eventType = manufacturerName + "." + webHookConfig.getManufacturerEventName();
        CloudEvent event = CloudEventBuilder.v1()
                .withId(cloudEventId) // 事件id
                .withSubject(webHookConfig.getCloudEventName()) // 事件主题
                .withSource(URI.create(webHookConfig.getCloudEventSource())) //事件源 uri
                .withDataContentType(webHookConfig.getDataContentType()) //转出数据格式
                .withType(eventType) //事件类型
                .withData(body) //数据
                .build();
    }

}
