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

package org.apache.eventmesh.client.http.consumer;

import org.apache.eventmesh.client.http.AbstractHttpClient;
import org.apache.eventmesh.client.http.EventMeshRetObj;
import org.apache.eventmesh.client.http.conf.EventMeshHttpClientConfig;
import org.apache.eventmesh.client.http.model.RequestParam;
import org.apache.eventmesh.client.http.util.HttpUtils;
import org.apache.eventmesh.client.tcp.common.EventMeshCommon;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.EventMeshThreadFactory;
import org.apache.eventmesh.common.ThreadPoolFactory;
import org.apache.eventmesh.common.exception.EventMeshException;
import org.apache.eventmesh.common.protocol.SubscriptionItem;
import org.apache.eventmesh.common.protocol.http.body.client.HeartbeatRequestBody;
import org.apache.eventmesh.common.protocol.http.body.client.SubscribeRequestBody;
import org.apache.eventmesh.common.protocol.http.body.client.UnSubscribeRequestBody;
import org.apache.eventmesh.common.protocol.http.common.ClientType;
import org.apache.eventmesh.common.protocol.http.common.EventMeshRetCode;
import org.apache.eventmesh.common.protocol.http.common.ProtocolKey;
import org.apache.eventmesh.common.protocol.http.common.ProtocolVersion;
import org.apache.eventmesh.common.protocol.http.common.RequestCode;
import org.apache.eventmesh.common.utils.JsonUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import io.netty.handler.codec.http.HttpMethod;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EventMeshHttpConsumer extends AbstractHttpClient implements AutoCloseable {

    private final transient ThreadPoolExecutor consumeExecutor;

    private static final List<SubscriptionItem> SUBSCRIPTIONS = Collections.synchronizedList(new ArrayList<>());

    private final transient ScheduledThreadPoolExecutor scheduler;

    public EventMeshHttpConsumer(final EventMeshHttpClientConfig eventMeshHttpClientConfig) throws EventMeshException {
        this(eventMeshHttpClientConfig, null);
    }

    public EventMeshHttpConsumer(final EventMeshHttpClientConfig eventMeshHttpClientConfig,
        final ThreadPoolExecutor customExecutor) throws EventMeshException {
        super(eventMeshHttpClientConfig);
        this.consumeExecutor = Optional.ofNullable(customExecutor).orElseGet(
            () -> ThreadPoolFactory.createThreadPoolExecutor(eventMeshHttpClientConfig.getConsumeThreadCore(),
                eventMeshHttpClientConfig.getConsumeThreadMax(), "EventMesh-client-consume"));
        this.scheduler = new ScheduledThreadPoolExecutor(Runtime.getRuntime().availableProcessors(),
            new EventMeshThreadFactory("HTTPClientScheduler", true));
    }

    /**
     * When receive message will callback the url.
     *
     * @param topicList    topic that be subscribed
     * @param subscribeUrl url will be trigger
     * @throws EventMeshException if subscribe failed
     */
    public void subscribe(final List<SubscriptionItem> topicList, final String subscribeUrl) throws EventMeshException {
        Objects.requireNonNull(topicList, "Subscribe item cannot be null");
        Objects.requireNonNull(subscribeUrl, "SubscribeUrl cannot be null");

        final RequestParam subscribeParam = buildCommonRequestParam()
            .addHeader(ProtocolKey.REQUEST_CODE, RequestCode.SUBSCRIBE.getRequestCode())
            .addBody(SubscribeRequestBody.TOPIC, JsonUtils.toJSONString(topicList))
            .addBody(SubscribeRequestBody.CONSUMERGROUP, eventMeshHttpClientConfig.getConsumerGroup())
            .addBody(SubscribeRequestBody.URL, subscribeUrl);

        final String target = selectEventMesh();
        try {
            final String subRes = HttpUtils.post(httpClient, target, subscribeParam);
            final EventMeshRetObj ret = JsonUtils.parseObject(subRes, EventMeshRetObj.class);
            if (Objects.requireNonNull(ret).getRetCode() != EventMeshRetCode.SUCCESS.getRetCode()) {
                throw new EventMeshException(ret.getRetCode(), ret.getRetMsg());
            }
            SUBSCRIPTIONS.addAll(topicList);
        } catch (Exception ex) {
            throw new EventMeshException(String.format("Subscribe topic error, target:%s", target), ex);
        }
    }

    // todo: remove http heartBeat?
    public void heartBeat(final List<SubscriptionItem> topicList, final String subscribeUrl) {
        Objects.requireNonNull(topicList, "Subscribe item cannot be null");
        Objects.requireNonNull(subscribeUrl, "SubscribeUrl cannot be null");

        scheduler.scheduleAtFixedRate(() -> {
            try {
                final List<HeartbeatRequestBody.HeartbeatEntity> heartbeatEntities = topicList.stream().map(subscriptionItem -> {
                    final HeartbeatRequestBody.HeartbeatEntity heartbeatEntity = new HeartbeatRequestBody.HeartbeatEntity();
                    heartbeatEntity.topic = subscriptionItem.getTopic();
                    heartbeatEntity.url = subscribeUrl;
                    return heartbeatEntity;
                }).collect(Collectors.toList());

                final RequestParam requestParam = buildCommonRequestParam()
                    .addHeader(ProtocolKey.REQUEST_CODE, RequestCode.HEARTBEAT.getRequestCode())
                    .addBody(HeartbeatRequestBody.CLIENTTYPE, ClientType.SUB.name())
                    .addBody(HeartbeatRequestBody.HEARTBEATENTITIES, JsonUtils.toJSONString(heartbeatEntities));
                final String target = selectEventMesh();
                final String res = HttpUtils.post(httpClient, target, requestParam);
                final EventMeshRetObj ret = JsonUtils.parseObject(res, EventMeshRetObj.class);
                if (EventMeshRetCode.SUCCESS.getRetCode() != Objects.requireNonNull(ret).getRetCode()) {
                    throw new EventMeshException(ret.getRetCode(), ret.getRetMsg());
                }
            } catch (Exception e) {
                log.error("send heartBeat error", e);
            }
        }, EventMeshCommon.HEARTBEAT, EventMeshCommon.HEARTBEAT, TimeUnit.MILLISECONDS);
    }

    /**
     * @param topicList      subscribe topic
     * @param unSubscribeUrl subscribeUrl
     * @throws EventMeshException if unsubscribe failed
     */
    public void unsubscribe(final List<String> topicList, final String unSubscribeUrl) throws EventMeshException {
        Objects.requireNonNull(topicList, "Topics cannot be null");
        Objects.requireNonNull(unSubscribeUrl, "unSubscribeUrl cannot be null");

        final RequestParam unSubscribeParam = buildCommonRequestParam()
            .addHeader(ProtocolKey.REQUEST_CODE, RequestCode.UNSUBSCRIBE.getRequestCode())
            .addBody(UnSubscribeRequestBody.TOPIC, JsonUtils.toJSONString(topicList))
            .addBody(UnSubscribeRequestBody.URL, unSubscribeUrl);

        final String target = selectEventMesh();
        try {
            final String unSubRes = HttpUtils.post(httpClient, target, unSubscribeParam);
            final EventMeshRetObj ret = JsonUtils.parseObject(unSubRes, EventMeshRetObj.class);

            if (EventMeshRetCode.SUCCESS.getRetCode() != Objects.requireNonNull(ret).getRetCode()) {
                throw new EventMeshException(ret.getRetCode(), ret.getRetMsg());
            }
            // todo: avoid concurrentModifiedException
            SUBSCRIPTIONS.removeIf(item -> topicList.contains(item.getTopic()));
        } catch (Exception e) {
            throw new EventMeshException(String.format("Unsubscribe topic error, target:%s", target), e);
        }
    }

    @Override
    public void close() throws EventMeshException {
        log.info("LiteConsumer shutdown begin.");
        super.close();

        if (consumeExecutor != null) {
            consumeExecutor.shutdown();
        }
        scheduler.shutdown();

        log.info("LiteConsumer shutdown end.");
    }

    private RequestParam buildCommonRequestParam() {
        return new RequestParam(HttpMethod.POST)
            .addHeader(ProtocolKey.ClientInstanceKey.ENV.getKey(), eventMeshHttpClientConfig.getEnv())
            .addHeader(ProtocolKey.ClientInstanceKey.IDC.getKey(), eventMeshHttpClientConfig.getIdc())
            .addHeader(ProtocolKey.ClientInstanceKey.IP.getKey(), eventMeshHttpClientConfig.getIp())
            .addHeader(ProtocolKey.ClientInstanceKey.PID.getKey(), eventMeshHttpClientConfig.getPid())
            .addHeader(ProtocolKey.ClientInstanceKey.SYS.getKey(), eventMeshHttpClientConfig.getSys())
            .addHeader(ProtocolKey.ClientInstanceKey.USERNAME.getKey(), eventMeshHttpClientConfig.getUserName())
            .addHeader(ProtocolKey.ClientInstanceKey.PASSWD.getKey(), eventMeshHttpClientConfig.getPassword())
            // add protocol version?
            .addHeader(ProtocolKey.VERSION, ProtocolVersion.V1.getVersion())
            .addHeader(ProtocolKey.LANGUAGE, Constants.LANGUAGE_JAVA)
            .setTimeout(Constants.DEFAULT_HTTP_TIME_OUT)
            .addBody(HeartbeatRequestBody.CONSUMERGROUP, eventMeshHttpClientConfig.getConsumerGroup());
    }
}
