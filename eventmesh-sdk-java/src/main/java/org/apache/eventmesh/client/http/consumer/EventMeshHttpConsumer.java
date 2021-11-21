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

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

import io.netty.handler.codec.http.HttpMethod;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EventMeshHttpConsumer extends AbstractHttpClient implements AutoCloseable {

    private final ThreadPoolExecutor consumeExecutor;

    private static final List<SubscriptionItem> subscription = Lists.newArrayList();

    private final ScheduledThreadPoolExecutor scheduler;

    public EventMeshHttpConsumer(EventMeshHttpClientConfig eventMeshHttpClientConfig) throws EventMeshException {
        this(eventMeshHttpClientConfig, null);
    }

    public EventMeshHttpConsumer(EventMeshHttpClientConfig eventMeshHttpClientConfig, ThreadPoolExecutor customExecutor)
        throws EventMeshException {
        super(eventMeshHttpClientConfig);
        this.consumeExecutor = Optional.ofNullable(customExecutor).orElseGet(
            () -> ThreadPoolFactory.createThreadPoolExecutor(eventMeshHttpClientConfig.getConsumeThreadCore(),
                eventMeshHttpClientConfig.getConsumeThreadMax(), "EventMesh-client-consume-")
        );
        this.scheduler = new ScheduledThreadPoolExecutor(
            Runtime.getRuntime().availableProcessors(),
            new ThreadFactoryBuilder().setNameFormat("HTTPClientScheduler").setDaemon(true).build()
        );
    }

    /**
     * When receive message will callback the url.
     *
     * @param topicList    topic that be subscribed
     * @param subscribeUrl url will be trigger
     * @throws EventMeshException if subscribe failed
     */
    public void subscribe(List<SubscriptionItem> topicList, String subscribeUrl) throws EventMeshException {
        Preconditions.checkNotNull(topicList, "Subscribe item cannot be null");
        Preconditions.checkNotNull(subscribeUrl, "SubscribeUrl cannot be null");
        RequestParam subscribeParam = buildCommonRequestParam()
            .addHeader(ProtocolKey.REQUEST_CODE, RequestCode.SUBSCRIBE.getRequestCode())
            .addBody(SubscribeRequestBody.TOPIC, JsonUtils.serialize(topicList))
            .addBody(SubscribeRequestBody.CONSUMERGROUP, eventMeshHttpClientConfig.getConsumerGroup())
            .addBody(SubscribeRequestBody.URL, subscribeUrl);

        String target = selectEventMesh();
        try {
            String subRes = HttpUtils.post(httpClient, target, subscribeParam);
            EventMeshRetObj ret = JsonUtils.deserialize(subRes, EventMeshRetObj.class);
            if (ret.getRetCode() != EventMeshRetCode.SUCCESS.getRetCode()) {
                throw new EventMeshException(ret.getRetCode(), ret.getRetMsg());
            }
            subscription.addAll(topicList);
        } catch (Exception ex) {
            throw new EventMeshException(String.format("Subscribe topic error, target:%s", target), ex);
        }
    }

    // todo: remove http heartBeat?
    public void heartBeat(List<SubscriptionItem> topicList, String subscribeUrl) {
        Preconditions.checkNotNull(topicList, "Subscribe item cannot be null");
        Preconditions.checkNotNull(subscribeUrl, "SubscribeUrl cannot be null");
        scheduler.scheduleAtFixedRate(() -> {
            try {
                List<HeartbeatRequestBody.HeartbeatEntity> heartbeatEntities = topicList.stream().map(subscriptionItem
                    -> {
                    HeartbeatRequestBody.HeartbeatEntity heartbeatEntity = new HeartbeatRequestBody.HeartbeatEntity();
                    heartbeatEntity.topic = subscriptionItem.getTopic();
                    heartbeatEntity.url = subscribeUrl;
                    return heartbeatEntity;
                }).collect(Collectors.toList());
                RequestParam requestParam = buildCommonRequestParam()
                    .addHeader(ProtocolKey.REQUEST_CODE, RequestCode.HEARTBEAT.getRequestCode())
                    .addBody(HeartbeatRequestBody.CLIENTTYPE, ClientType.SUB.name())
                    .addBody(HeartbeatRequestBody.HEARTBEATENTITIES, JsonUtils.serialize(heartbeatEntities));
                String target = selectEventMesh();
                String res = HttpUtils.post(httpClient, target, requestParam);
                EventMeshRetObj ret = JsonUtils.deserialize(res, EventMeshRetObj.class);
                if (ret.getRetCode() != EventMeshRetCode.SUCCESS.getRetCode()) {
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
    public void unsubscribe(List<String> topicList, String unSubscribeUrl) throws EventMeshException {
        Preconditions.checkNotNull(topicList, "Topics cannot be null");
        Preconditions.checkNotNull(unSubscribeUrl, "unSubscribeUrl cannot be null");
        RequestParam unSubscribeParam = buildCommonRequestParam()
            .addHeader(ProtocolKey.REQUEST_CODE, RequestCode.UNSUBSCRIBE.getRequestCode())
            .addBody(UnSubscribeRequestBody.TOPIC, JsonUtils.serialize(topicList))
            .addBody(UnSubscribeRequestBody.URL, unSubscribeUrl);
        String target = selectEventMesh();
        try {
            String unSubRes = HttpUtils.post(httpClient, target, unSubscribeParam);
            EventMeshRetObj ret = JsonUtils.deserialize(unSubRes, EventMeshRetObj.class);

            if (ret.getRetCode() != EventMeshRetCode.SUCCESS.getRetCode()) {
                throw new EventMeshException(ret.getRetCode(), ret.getRetMsg());
            }
            // todo: avoid concurrentModifiedException
            subscription.removeIf(item -> topicList.contains(item.getTopic()));
        } catch (Exception ex) {
            throw new EventMeshException(String.format("Unsubscribe topic error, target:%s", target), ex);
        }
    }

    @Override
    public void close() throws EventMeshException {
        log.info("LiteConsumer shutting down");
        super.close();
        if (consumeExecutor != null) {
            consumeExecutor.shutdown();
        }
        scheduler.shutdown();
        log.info("LiteConsumer shutdown");
    }

    private String selectEventMesh() {
        // todo: target endpoint maybe destroy, should remove the bad endpoint
        if (eventMeshHttpClientConfig.isUseTls()) {
            return Constants.HTTPS_PROTOCOL_PREFIX + eventMeshServerSelector.select();
        } else {
            return Constants.HTTP_PROTOCOL_PREFIX + eventMeshServerSelector.select();
        }
    }

    private RequestParam buildCommonRequestParam() {
        return new RequestParam(HttpMethod.POST)
            .addHeader(ProtocolKey.ClientInstanceKey.ENV, eventMeshHttpClientConfig.getEnv())
            .addHeader(ProtocolKey.ClientInstanceKey.IDC, eventMeshHttpClientConfig.getIdc())
            .addHeader(ProtocolKey.ClientInstanceKey.IP, eventMeshHttpClientConfig.getIp())
            .addHeader(ProtocolKey.ClientInstanceKey.PID, eventMeshHttpClientConfig.getPid())
            .addHeader(ProtocolKey.ClientInstanceKey.SYS, eventMeshHttpClientConfig.getSys())
            .addHeader(ProtocolKey.ClientInstanceKey.USERNAME, eventMeshHttpClientConfig.getUserName())
            .addHeader(ProtocolKey.ClientInstanceKey.PASSWD, eventMeshHttpClientConfig.getPassword())
            .addHeader(ProtocolKey.VERSION, ProtocolVersion.V1.getVersion())
            .addHeader(ProtocolKey.LANGUAGE, Constants.LANGUAGE_JAVA)
            .setTimeout(Constants.DEFAULT_HTTP_TIME_OUT)
            .addBody(HeartbeatRequestBody.CONSUMERGROUP, eventMeshHttpClientConfig.getConsumerGroup());
    }
}
