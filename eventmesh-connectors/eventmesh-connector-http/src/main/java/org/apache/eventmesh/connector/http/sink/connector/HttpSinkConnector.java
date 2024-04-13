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

package org.apache.eventmesh.connector.http.sink.connector;


import org.apache.eventmesh.connector.http.sink.config.HttpSinkConfig;
import org.apache.eventmesh.openconnect.api.config.Config;
import org.apache.eventmesh.openconnect.api.connector.ConnectorContext;
import org.apache.eventmesh.openconnect.api.connector.SinkConnectorContext;
import org.apache.eventmesh.openconnect.api.sink.Sink;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;

import org.apache.commons.lang3.builder.ToStringBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

@Slf4j
public class HttpSinkConnector implements Sink {

    private OkHttpClient okHttpClient;

    private HttpSinkConfig httpSinkConfig;

    private String messageSendUrl;

    private volatile boolean isRunning = false;


    @Override
    public Class<? extends Config> configClass() {
        return HttpSinkConfig.class;
    }

    @Override
    public void init(Config config) throws Exception {
        httpSinkConfig = (HttpSinkConfig) config;
        doInit();
    }

    @Override
    public void init(ConnectorContext connectorContext) throws Exception {
        SinkConnectorContext sinkConnectorContext = (SinkConnectorContext) connectorContext;
        this.httpSinkConfig = (HttpSinkConfig) sinkConnectorContext.getSinkConfig();
        doInit();
    }

    @SneakyThrows
    private void doInit() {
        this.messageSendUrl = this.httpSinkConfig.getConnectorConfig().getAddress() + this.httpSinkConfig.getConnectorConfig().getPath();
        this.okHttpClient = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build();
    }

    @Override
    public void start() throws Exception {
        this.isRunning = true;
    }

    @Override
    public void commit(ConnectRecord record) {

    }

    @Override
    public String name() {
        return this.httpSinkConfig.getConnectorConfig().getConnectorName();
    }

    @Override
    public void stop() throws Exception {
        this.isRunning = false;
    }

    public boolean isRunning() {
        return isRunning;
    }

    @Override
    public void put(List<ConnectRecord> sinkRecords) {
        for (ConnectRecord sinkRecord : sinkRecords) {
            try {
                if (Objects.isNull(sinkRecord)) {
                    log.warn("ConnectRecord data is null, ignore.");
                    continue;
                }
            } catch (Exception e) {
                log.error("Failed to sink message via HTTP.", e);
            }
            sendMessage(sinkRecord);
        }
    }

    @SneakyThrows
    private void sendMessage(ConnectRecord record) {
        // Construct HTTP request
        MediaType mediaType = MediaType.parse("application/json; charset=utf-8");
        String data = new String((byte[]) record.getData(), StandardCharsets.UTF_8);
        RequestBody body = RequestBody.create(mediaType, data);
        Request request = new Request.Builder()
            .url(this.messageSendUrl)
            .post(body)
            .build();

        // Send HTTP request
        Response response = okHttpClient.newCall(request).execute();

        // Verify HTTP response
        if (!response.isSuccessful()) {
            log.error("server response: {}", ToStringBuilder.reflectionToString(response));
            throw new IOException("Unexpected code " + response.code());
        }
        ResponseBody responseBody = response.body();
        if (responseBody == null) {
            throw new IOException("Response body is null.");
        }
        // TODO Define the response result template
    }

}
