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

import org.apache.eventmesh.client.http.model.RequestParam;
import org.apache.eventmesh.client.http.util.HttpUtils;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.connector.wecom.config.WeComMessageTemplateType;
import org.apache.eventmesh.connector.wecom.constants.ConnectRecordExtensionKeys;
import org.apache.eventmesh.connector.wecom.sink.config.WeComSinkConfig;
import org.apache.eventmesh.openconnect.api.config.Config;
import org.apache.eventmesh.openconnect.api.connector.ConnectorContext;
import org.apache.eventmesh.openconnect.api.connector.SinkConnectorContext;
import org.apache.eventmesh.openconnect.api.sink.Sink;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import io.netty.handler.codec.http.HttpMethod;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

/**
 * WeCom sink connector.
 * WeCom doc: <a href="https://developer.work.weixin.qq.com/document/path/90236">...</a>
 */
@Slf4j
public class WeComSinkConnector implements Sink {

    public static final Cache<String, String> AUTH_CACHE = CacheBuilder.newBuilder()
        .initialCapacity(12)
        .maximumSize(10)
        .concurrencyLevel(5)
        .expireAfterWrite(20, TimeUnit.MINUTES)
        .build();

    private CloseableHttpClient httpClient;

    private WeComSinkConfig sinkConfig;

    private volatile boolean isRunning = false;

    public static final String ACCESS_TOKEN_CACHE_KEY = "access_token";

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
    public void stop() {
        isRunning = false;
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
                String accessToken = getAccessToken();
                sendMessage(record, accessToken);
            } catch (Exception e) {
                log.error("Failed to sink message to DingDing.", e);
            }
        }
    }

    @SneakyThrows
    private void sendMessage(ConnectRecord record, String accessToken) {
        RequestParam param = new RequestParam(HttpMethod.POST).setTimeout(Constants.DEFAULT_HTTP_TIME_OUT);
        String toUserId = record.getExtension(ConnectRecordExtensionKeys.WECOM_TO_USER_ID);
        String toPartyId = record.getExtension(ConnectRecordExtensionKeys.WECOM_TO_PARTY_ID);
        String toTagId = record.getExtension(ConnectRecordExtensionKeys.WECOM_TO_TAG_ID);
        if (toUserId == null && toPartyId == null && toTagId == null) {
            throw new IllegalArgumentException("toUserId, toPartyId, toTagId Not allowed All empty.");
        }
        param.addQueryParam("access_token", accessToken);
        param.addBody("touser", toUserId);
        param.addBody("toparty", toPartyId);
        param.addBody("totag", toTagId);
        WeComMessageTemplateType templateType = WeComMessageTemplateType.of(
                Optional.ofNullable(record.getExtension(ConnectRecordExtensionKeys.WECOM_MESSAGE_TEMPLATE_TYPE_KEY))
                        .orElse(WeComMessageTemplateType.TEXT.getTemplateKey()));
        param.addBody("msgtype", templateType.getTemplateKey());
        param.addBody("agentid", sinkConfig.getSinkConnectorConfig().getAppAgentId());
        Map<String, String> contentMap = new HashMap<>();
        contentMap.put("content", new String((byte[]) record.getData()));
        param.addBody("text", JsonUtils.toJSONString(contentMap));
        final String target = "https://qyapi.weixin.qq.com/cgi-bin/message/send";
        String sendMessageResult = HttpUtils.post(httpClient, target, param);
        SendMessageDTO sendMessageDTO = Objects.requireNonNull(JsonUtils.parseObject(sendMessageResult, SendMessageDTO.class));
        if (sendMessageDTO.getErrorCode() != 0) {
            throw new IllegalAccessException(String.format("Send message to weCom error! errorCode=%s, errorMessage=%s",
                sendMessageDTO.getErrorCode(), sendMessageDTO.getErrorMessage()));
        }
    }

    @SneakyThrows
    private String getAccessToken() {
        return AUTH_CACHE.get(ACCESS_TOKEN_CACHE_KEY, () -> {
            try {
                String uri = "https://qyapi.weixin.qq.com/cgi-bin/gettoken";
                RequestParam requestParam = new RequestParam(HttpMethod.GET).setTimeout(Constants.DEFAULT_HTTP_TIME_OUT);
                requestParam.addQueryParam("corpid", sinkConfig.getSinkConnectorConfig().getCorpId());
                requestParam.addQueryParam("corpsecret", sinkConfig.getSinkConnectorConfig().getAppSecret());
                String resultStr = HttpUtils.get(httpClient, uri, requestParam);
                AccessTokenDTO accessTokenDTO = Objects.requireNonNull(JsonUtils.parseObject(resultStr, AccessTokenDTO.class));
                return accessTokenDTO.getAccessToken();
            } catch (Exception e) {
                log.error("Get WeCom access token error.", e);
                throw e;
            }
        });
    }
}
