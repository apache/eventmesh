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

package org.apache.eventmesh.connector.feishu.sink.connector;

import static org.apache.eventmesh.common.Constants.FEISHU_CONTENT;
import static org.apache.eventmesh.common.Constants.FEISHU_MSG_TYPE;
import static org.apache.eventmesh.common.Constants.FEISHU_RECEIVE_ID;
import static org.apache.eventmesh.common.Constants.FEISHU_SEND_MESSAGE_API;
import static org.apache.eventmesh.common.Constants.FEISHU_UUID;

import org.apache.eventmesh.connector.feishu.sink.config.FeishuSinkConfig;
import org.apache.eventmesh.connector.feishu.sink.config.SinkConnectorConfig;
import org.apache.eventmesh.openconnect.api.config.Config;
import org.apache.eventmesh.openconnect.api.connector.ConnectorContext;
import org.apache.eventmesh.openconnect.api.connector.SinkConnectorContext;
import org.apache.eventmesh.openconnect.api.sink.Sink;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;


import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.github.rholder.retry.Attempt;
import com.github.rholder.retry.RetryListener;
import com.github.rholder.retry.Retryer;
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategies;
import com.github.rholder.retry.WaitStrategies;
import com.lark.oapi.Client;
import com.lark.oapi.core.response.RawResponse;
import com.lark.oapi.core.token.AccessTokenType;
import com.lark.oapi.service.im.v1.enums.MsgTypeEnum;
import com.lark.oapi.service.im.v1.model.ext.MessageText;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FeishuSinkConnector implements Sink {

    private FeishuSinkConfig sinkConfig;

    private Client feishuClient;

    private static final int MAX_RETRY_TIME = 3;

    private static final int FIXED_WAIT_SECOND = 1;

    private final Retryer<Boolean> retryer =
            RetryerBuilder.<Boolean>newBuilder()
                          .retryIfException()
                          .retryIfResult(res -> !res)
                          .withWaitStrategy(
                                  WaitStrategies.fixedWait(FIXED_WAIT_SECOND, TimeUnit.SECONDS))
                          .withStopStrategy(StopStrategies.stopAfterAttempt(MAX_RETRY_TIME))
                          .withRetryListener(
                                  new RetryListener() {
                                      @Override
                                      public <V> void onRetry(Attempt<V> attempt) {
                                          long times = attempt.getAttemptNumber();
                                          log.warn("retry invoke http,times={}", times);
                                      }
                                  })
                          .build();

    @Override
    public Class<? extends Config> configClass() {
        return FeishuSinkConfig.class;
    }

    @Override
    public void init(Config config) throws Exception {
        // init config for feishu sink connector
        this.sinkConfig = (FeishuSinkConfig) config;
        doInit();
    }

    @Override
    public void init(ConnectorContext connectorContext) throws Exception {
        // init config for feishu sink connector
        SinkConnectorContext sinkConnectorContext = (SinkConnectorContext) connectorContext;
        this.sinkConfig = (FeishuSinkConfig) sinkConnectorContext.getSinkConfig();
        doInit();
    }

    private void doInit() {
        this.feishuClient = Client.newBuilder(this.getConfig().getAppId(), this.getConfig().getAppSecret()).
                                  requestTimeout(3,TimeUnit.SECONDS)
                                  .build();
    }

    @Override
    public void start() {
    }

    @Override
    public void commit(ConnectRecord record) {

    }

    @Override
    public String name() {
        return this.sinkConfig.getConnectorConfig().getConnectorName();
    }

    @Override
    public void stop() {
    }

    @Override
    public void put(List<ConnectRecord> sinkRecords) {
        SinkConnectorConfig connectorConfig = getConfig();
        try {
            for (ConnectRecord connectRecord : sinkRecords) {
                AtomicReference<RawResponse> response = new AtomicReference<>();
                retryer.call(() -> {
                    Map<String, Object> body = new HashMap<>();
                    body.put(FEISHU_RECEIVE_ID, connectorConfig.getReceiveId());
                    body.put(FEISHU_CONTENT, MessageText.newBuilder().text(connectRecord.getData().toString()).build());
                    body.put(FEISHU_MSG_TYPE, MsgTypeEnum.MSG_TYPE_TEXT.getValue());
                    body.put(FEISHU_UUID, UUID.randomUUID().toString());
                    response.set(feishuClient.post(FEISHU_SEND_MESSAGE_API + connectorConfig.getReceiveIdType(), body, AccessTokenType.Tenant));
                    if (response.get().getStatusCode() != 200){
                        log.error("request feishu open api err{}", new String(response.get().getBody()));
                        return false;
                    }
                    return true;
                });
            }
        } catch (Exception e) {
            log.error("failed to put message to feishu", e);
        }
    }

    public SinkConnectorConfig getConfig() {
        return this.sinkConfig.getConnectorConfig();
    }
}
