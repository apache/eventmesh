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

package org.apache.eventmesh.connector.lark.sink.connector;

import static org.apache.eventmesh.connector.lark.sink.ImServiceHandler.create;

import org.apache.eventmesh.common.config.connector.Config;
import org.apache.eventmesh.common.config.connector.lark.LarkSinkConfig;
import org.apache.eventmesh.common.config.connector.lark.SinkConnectorConfig;
import org.apache.eventmesh.connector.lark.ConfigUtils;
import org.apache.eventmesh.connector.lark.sink.ImServiceHandler;
import org.apache.eventmesh.openconnect.api.connector.ConnectorContext;
import org.apache.eventmesh.openconnect.api.connector.SinkConnectorContext;
import org.apache.eventmesh.openconnect.api.sink.Sink;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.github.rholder.retry.RetryException;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.lark.oapi.Client;
import com.lark.oapi.core.enums.AppType;
import com.lark.oapi.core.request.SelfBuiltTenantAccessTokenReq;
import com.lark.oapi.core.response.TenantAccessTokenResp;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LarkSinkConnector implements Sink {

    public static final String TENANT_ACCESS_TOKEN = "tenant_access_token";

    /**
     * Global Access Credential Manager to replace lark build-in tokenCache.
     * <p>
     * If you plan to extend the method of obtaining other credentials,
     * you can refer to the implementation of {@link #getTenantAccessToken(String, String)}
     * <p>
     * If the expiration mechanism provided by lark conflicts with the expiration time set in AUTH_CACHE,
     * you can try to modify it.
     */
    public static final Cache<String, String> AUTH_CACHE = CacheBuilder.newBuilder()
        .initialCapacity(12)
        .maximumSize(10)
        .concurrencyLevel(5)
        .expireAfterWrite(30, TimeUnit.MINUTES)
        .build();

    private LarkSinkConfig sinkConfig;

    private ImServiceHandler imServiceHandler;

    private final AtomicBoolean started = new AtomicBoolean(false);

    @Override
    public Class<? extends Config> configClass() {
        return LarkSinkConfig.class;
    }

    @Override
    public void init(Config config) {
    }

    @Override
    public void init(ConnectorContext connectorContext) {
        // init config for lark sink connector
        SinkConnectorContext sinkConnectorContext = (SinkConnectorContext) connectorContext;
        this.sinkConfig = (LarkSinkConfig) sinkConnectorContext.getSinkConfig();

        SinkConnectorConfig sinkConnectorConfig = sinkConfig.getSinkConnectorConfig();
        ConfigUtils.validateSinkConfiguration(sinkConnectorConfig);

        imServiceHandler = create(sinkConnectorConfig);
    }

    @Override
    public void start() {
        if (!started.compareAndSet(false, true)) {
            log.info("LarkSinkConnector has been started.");
        }
    }

    @Override
    public void commit(ConnectRecord record) {
        // Sink does not need to implement
    }

    @Override
    public String name() {
        return this.sinkConfig.getSinkConnectorConfig().getConnectorName();
    }

    @Override
    public void stop() {
        if (!started.compareAndSet(true, false)) {
            log.info("LarkSinkConnector has not started yet.");
        }
    }

    @Override
    public void put(List<ConnectRecord> sinkRecords) {
        for (ConnectRecord connectRecord : sinkRecords) {
            try {
                if (Boolean.parseBoolean(sinkConfig.getSinkConnectorConfig().getSinkAsync())) {
                    imServiceHandler.sinkAsync(connectRecord);
                } else {
                    imServiceHandler.sink(connectRecord);
                }
            } catch (ExecutionException | RetryException e) {
                log.error("Failed to sink event to lark", e);
            }
        }
    }

    @SneakyThrows
    public static String getTenantAccessToken(String appId, String appSecret) {
        return AUTH_CACHE.get(TENANT_ACCESS_TOKEN, () -> {

            Client client = Client.newBuilder(appId, appSecret)
                .appType(AppType.SELF_BUILT)
                .logReqAtDebug(true)
                .build();

            TenantAccessTokenResp resp = client.ext().getTenantAccessTokenBySelfBuiltApp(
                SelfBuiltTenantAccessTokenReq.newBuilder()
                    .appSecret(appSecret)
                    .appId(appId)
                    .build());
            return resp.getTenantAccessToken();
        });
    }
}
