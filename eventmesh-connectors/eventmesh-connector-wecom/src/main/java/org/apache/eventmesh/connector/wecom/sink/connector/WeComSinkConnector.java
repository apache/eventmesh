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

package org.apache.eventmesh.connector.wecom.sink.connector;

import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.config.connector.Config;
import org.apache.eventmesh.common.config.connector.wecom.WeComSinkConfig;
import org.apache.eventmesh.common.enums.EventMeshDataContentType;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.connector.wecom.config.WeComMessageTemplateType;
import org.apache.eventmesh.connector.wecom.constants.ConnectRecordExtensionKeys;
import org.apache.eventmesh.openconnect.api.connector.ConnectorContext;
import org.apache.eventmesh.openconnect.api.connector.SinkConnectorContext;
import org.apache.eventmesh.openconnect.api.sink.Sink;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

/**
 * WeCom sink connector.
 * WeCom doc: <a href="https://developer.work.weixin.qq.com/document/path/90236">...</a>
 */
@Slf4j
public class WeComSinkConnector implements Sink {

    private static final String ROBOT_WEBHOOK_URL_PREFIX = "https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=";

    private CloseableHttpClient httpClient;

    private WeComSinkConfig sinkConfig;

    private volatile boolean isRunning = false;

    @Override
    public Class<? extends Config> configClass() {
        return WeComSinkConfig.class;
    }

    @Override
    public void init(Config config) {
        this.sinkConfig = (WeComSinkConfig) config;
        httpClient = HttpClientBuilder.create().build();
    }

    @Override
    public void init(ConnectorContext connectorContext) {
        SinkConnectorContext sinkConnectorContext = (SinkConnectorContext) connectorContext;
        this.sinkConfig = (WeComSinkConfig) sinkConnectorContext.getSinkConfig();
        httpClient = HttpClientBuilder.create().build();
    }

    @Override
    public void start() {
        isRunning = true;
    }

    @Override
    public void commit(ConnectRecord record) {

    }

    @Override
    public String name() {
        return this.sinkConfig.getSinkConnectorConfig().getConnectorName();
    }

    @Override
    public void stop() throws IOException {
        isRunning = false;
        httpClient.close();
    }

    public boolean isRunning() {
        return isRunning;
    }

    @Override
    public void put(List<ConnectRecord> sinkRecords) {
        for (ConnectRecord record : sinkRecords) {
            try {
                if (Objects.isNull(record.getData())) {
                    log.warn("ConnectRecord data is null, ignore.");
                    continue;
                }
                sendMessage(record);
            } catch (Exception e) {
                log.error("Failed to sink message to WeCom.", e);
            }
        }
    }

    @SneakyThrows
    private void sendMessage(ConnectRecord record) {
        final String target = ROBOT_WEBHOOK_URL_PREFIX + sinkConfig.getSinkConnectorConfig().getRobotWebhookKey();
        SendMessageRequest request = new SendMessageRequest();
        HttpPost httpPost = new HttpPost(target);
        httpPost.addHeader("Content-Type", EventMeshDataContentType.JSON.getCode());
        WeComMessageTemplateType templateType = WeComMessageTemplateType.of(
            Optional.ofNullable(record.getExtension(ConnectRecordExtensionKeys.WECOM_MESSAGE_TEMPLATE_TYPE))
                .orElse(WeComMessageTemplateType.PLAIN_TEXT.getTemplateType()));
        Map<String, Object> contentMap = new HashMap<>();
        if (WeComMessageTemplateType.PLAIN_TEXT == templateType) {
            contentMap.put("content", new String((byte[]) record.getData()));
            request.setTextContent(contentMap);
        } else if (WeComMessageTemplateType.MARKDOWN == templateType) {
            contentMap.put("content", new String((byte[]) record.getData()));
            request.setMarkdownContent(contentMap);
        }
        request.setMessageType(templateType.getTemplateKey());
        httpPost.setEntity(new StringEntity(Objects.requireNonNull(JsonUtils.toJSONString(request)), ContentType.APPLICATION_JSON));
        CloseableHttpResponse httpResponse = httpClient.execute(httpPost);
        String resultStr = EntityUtils.toString(httpResponse.getEntity(), Constants.DEFAULT_CHARSET);
        SendMessageResponse sendMessageResponse = Objects.requireNonNull(JsonUtils.parseObject(resultStr, SendMessageResponse.class));
        if (sendMessageResponse.getErrorCode() != 0) {
            throw new IllegalAccessException(String.format("Send message to weCom error! errorCode=%s, errorMessage=%s",
                sendMessageResponse.getErrorCode(), sendMessageResponse.getErrorMessage()));
        }
    }
}