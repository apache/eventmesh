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

package org.apache.eventmesh.connector.wechat.sink.connector;

import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.connector.wechat.sink.config.WeChatSinkConfig;
import org.apache.eventmesh.openconnect.api.config.Config;
import org.apache.eventmesh.openconnect.api.connector.ConnectorContext;
import org.apache.eventmesh.openconnect.api.connector.SinkConnectorContext;
import org.apache.eventmesh.openconnect.api.sink.Sink;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;

import org.apache.commons.lang3.builder.ToStringBuilder;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * WeChat sink connector. WeChat doc: <a href="https://developer.work.weixin.qq.com/document/path/90236">...</a>
 */
@Slf4j
public class WeChatSinkConnector implements Sink {

    public static final Cache<String, String> ACCESS_TOKEN_CACHE = CacheBuilder.newBuilder()
        .initialCapacity(12)
        .maximumSize(10)
        .concurrencyLevel(5)
        .expireAfterWrite(120, TimeUnit.MINUTES)
        .build();

    public static final String ACCESS_TOKEN_CACHE_KEY = "access_token";

    private static final String ACCESS_TOKEN_URL = "https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential&appid=%s&secret=%s";

    private static final String MESSAGE_SEND_URL = "https://api.weixin.qq.com/cgi-bin/message/template/send?access_token=%s";

    private OkHttpClient okHttpClient;

    private WeChatSinkConfig sinkConfig;

    private volatile boolean isRunning = false;

    @Override
    public Class<? extends Config> configClass() {
        return WeChatSinkConfig.class;
    }

    @Override
    public void init(Config config) {
        this.sinkConfig = (WeChatSinkConfig) config;
        okHttpClient = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build();
    }

    @Override
    public void init(ConnectorContext connectorContext) {
        SinkConnectorContext sinkConnectorContext = (SinkConnectorContext) connectorContext;
        this.sinkConfig = (WeChatSinkConfig) sinkConnectorContext.getSinkConfig();
        okHttpClient = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build();
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
                log.error("Failed to sink message to WeChat.", e);
            }
        }
    }

    @SneakyThrows
    private void sendMessage(ConnectRecord record) {
        // get access token
        String accessToken = getAccessToken();
        MediaType mediaType = MediaType.parse("application/json; charset=utf-8");
        RequestBody body = RequestBody.create(mediaType, new String((byte[]) record.getData()));
        Request request = new Request.Builder()
            .url(String.format(MESSAGE_SEND_URL, accessToken))
            .post(body)
            .build();
        try (Response response = okHttpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.error("server response: {}", ToStringBuilder.reflectionToString(response));
                throw new IOException("Unexpected code " + response);
            }

            ResponseBody responseBody = response.body();
            if (responseBody == null) {
                throw new IOException("Response body is null.");
            }

            String jsonStr = responseBody.string();
            TemplateMessageResponse messageResponse = JsonUtils.parseObject(jsonStr, TemplateMessageResponse.class);
            if (messageResponse == null) {
                throw new IOException("message response is null.");
            }

            if (messageResponse.getErrcode() != 0) {
                throw new IllegalAccessException(String.format("Send message to WeChat error! errorCode=%s, errorMessage=%s",
                    messageResponse.getErrcode(), messageResponse.getErrmsg()));
            }
        }

    }

    @SneakyThrows
    private String getAccessToken() {
        return ACCESS_TOKEN_CACHE.get(ACCESS_TOKEN_CACHE_KEY,
            () -> {
                Request tokenRequest = new Request.Builder()
                    .url(String.format(ACCESS_TOKEN_URL, sinkConfig.getSinkConnectorConfig().getAppId(),
                        sinkConfig.getSinkConnectorConfig().getAppSecret()))
                    .get()
                    .build();
                String accessToken;
                try (Response response = okHttpClient.newCall(tokenRequest).execute()) {
                    if (!response.isSuccessful()) {
                        log.error("server response: {}", ToStringBuilder.reflectionToString(response));
                        throw new IOException("Unexpected code " + response);
                    }

                    String json = Objects.requireNonNull(response.body()).string();
                    JSONObject jsonObject = JSON.parseObject(json);
                    accessToken = Objects.requireNonNull(jsonObject).getString(ACCESS_TOKEN_CACHE_KEY);
                    ACCESS_TOKEN_CACHE.put(ACCESS_TOKEN_CACHE_KEY, accessToken);
                }

                return accessToken;
            });
    }
}