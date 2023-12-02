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

import org.apache.eventmesh.connector.lark.sink.config.LarkSinkConfig;
import org.apache.eventmesh.connector.lark.sink.config.SinkConnectorConfig;
import org.apache.eventmesh.openconnect.api.config.Config;
import org.apache.eventmesh.openconnect.api.connector.ConnectorContext;
import org.apache.eventmesh.openconnect.api.connector.SinkConnectorContext;
import org.apache.eventmesh.openconnect.api.sink.Sink;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.github.rholder.retry.Attempt;
import com.github.rholder.retry.RetryException;
import com.github.rholder.retry.RetryListener;
import com.github.rholder.retry.Retryer;
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategies;
import com.github.rholder.retry.WaitStrategies;
import com.lark.oapi.Client;
import com.lark.oapi.core.request.RequestOptions;
import com.lark.oapi.core.utils.Jsons;
import com.lark.oapi.core.utils.Lists;
import com.lark.oapi.service.im.v1.ImService;
import com.lark.oapi.service.im.v1.enums.MsgTypeEnum;
import com.lark.oapi.service.im.v1.enums.ReceiveIdTypeEnum;
import com.lark.oapi.service.im.v1.model.CreateMessageReq;
import com.lark.oapi.service.im.v1.model.CreateMessageReqBody;
import com.lark.oapi.service.im.v1.model.CreateMessageResp;
import com.lark.oapi.service.im.v1.model.ext.MessageText;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LarkSinkConnector implements Sink {

    private LarkSinkConfig sinkConfig;

    private ImService imService;

    // todo maybe can set whether retry
    private Retryer<Boolean> retryer;

    private final AtomicBoolean started = new AtomicBoolean(false);

    @Override
    public Class<? extends Config> configClass() {
        return LarkSinkConfig.class;
    }

    @Override
    public void init(Config config) {
        // todo Will be removed in the next version.see(https://github.com/apache/eventmesh/issues/4565#issuecomment-1817901972)
    }

    @Override
    public void init(ConnectorContext connectorContext) {
        // init config for lark sink connector
        SinkConnectorContext sinkConnectorContext = (SinkConnectorContext) connectorContext;
        this.sinkConfig = (LarkSinkConfig) sinkConnectorContext.getSinkConfig();

        SinkConnectorConfig sinkConnectorConfig = sinkConfig.getSinkConnectorConfig();
        this.imService = Client.newBuilder(sinkConnectorConfig.getAppId(), sinkConnectorConfig.getAppSecret())
                .requestTimeout(3, TimeUnit.SECONDS).build().im();

        long fixedWait = Long.parseLong(sinkConnectorConfig.getRetryDelayInMills());
        int maxRetryTimes = Integer.parseInt(sinkConnectorConfig.getMaxRetryTimes());
        retryer = RetryerBuilder.<Boolean>newBuilder()
                .retryIfException()
                .retryIfResult(Boolean.FALSE::equals)
                .withWaitStrategy(WaitStrategies.fixedWait(fixedWait, TimeUnit.MILLISECONDS))
                .withStopStrategy(StopStrategies.stopAfterAttempt(maxRetryTimes))
                .withRetryListener(new RetryListener() {
                    @Override
                    public <V> void onRetry(Attempt<V> attempt) {
                        log.warn("Retry sink event to lark | times=[{}]", attempt.getAttemptNumber());
                    }
                })
                .build();
    }

    @Override
    public void start() {
        if (!started.compareAndSet(false, true)) {
            log.info("LarkSinkConnector has been started.");
        }
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
        if (!started.compareAndSet(true, false)) {
            log.info("LarkSinkConnector has not started yet.");
        }
    }

    /**
     * require the ConnectRecord#data actually type is byte array, and use UTF-8
     * @param sinkRecords
     */
    @Override
    public void put(List<ConnectRecord> sinkRecords) {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("Content-Type", Lists.newArrayList("application/json; charset=utf-8"));
        RequestOptions requestOptions = RequestOptions.newBuilder()
                .tenantAccessToken(sinkConfig.getSinkConnectorConfig().getTenantAccessToken())
                .headers(headers)
                .build();

        for (ConnectRecord connectRecord : sinkRecords) {
            try {
                retryer.call(() -> {

                    CreateMessageResp resp = imService.message().create(convert(connectRecord), requestOptions);
                    if (resp.getCode() != 0) {
                        log.warn("Sinking event to lark failure | code:[{}] | msg:[{}] | err:[{}]", resp.getCode(), resp.getMsg(), resp.getError());
                        return false;
                    }

                    log.info("{}", Jsons.DEFAULT.toJson(resp.getData()));

                    log.info("{}", Jsons.DEFAULT.toJson(resp.getRawResponse()));

                    log.info("{}", resp.getRequestId());

                    return true;
                });
            } catch (ExecutionException | RetryException e) {
                log.error("Failed to sink event to lark", e);
            }
        }
    }

    private CreateMessageReq convert(ConnectRecord connectRecord) {
        return CreateMessageReq.newBuilder()
                // todo whether could support more receive type
                .receiveIdType(ReceiveIdTypeEnum.OPEN_ID.getValue())
                .createMessageReqBody(
                        CreateMessageReqBody.newBuilder()
                                .receiveId(sinkConfig.getSinkConnectorConfig().getReceiveId())
                                // todo whether could support more msg type
                                .msgType(MsgTypeEnum.MSG_TYPE_TEXT.getValue())
                                .content(MessageText.newBuilder().text(new String((byte[]) connectRecord.getData())).build())
                                // todo watch out retry message with uuid related
                                .uuid(UUID.randomUUID().toString())
                                .build()
                )
                .build();
    }
}
