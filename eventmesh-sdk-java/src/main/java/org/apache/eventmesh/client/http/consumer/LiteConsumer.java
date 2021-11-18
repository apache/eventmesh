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

import org.apache.eventmesh.client.http.AbstractLiteClient;
import org.apache.eventmesh.client.http.EventMeshRetObj;
import org.apache.eventmesh.client.http.conf.LiteClientConfig;
import org.apache.eventmesh.client.http.http.HttpUtil;
import org.apache.eventmesh.client.http.http.RequestParam;
import org.apache.eventmesh.client.tcp.common.EventMeshCommon;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.EventMeshException;
import org.apache.eventmesh.common.ThreadPoolFactory;
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

import org.apache.http.impl.client.CloseableHttpClient;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

import io.netty.handler.codec.http.HttpMethod;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LiteConsumer extends AbstractLiteClient {

    private ThreadPoolExecutor consumeExecutor;

    protected LiteClientConfig eventMeshClientConfig;

    private static final List<SubscriptionItem> subscription = Lists.newArrayList();

    private static final AtomicBoolean started = new AtomicBoolean(Boolean.FALSE);

    protected final ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(
        Runtime.getRuntime().availableProcessors(),
        new ThreadFactoryBuilder().setNameFormat("TCPClientScheduler").setDaemon(true).build()
    );

    public LiteConsumer(LiteClientConfig liteClientConfig) {
        this(liteClientConfig,
            ThreadPoolFactory.createThreadPoolExecutor(liteClientConfig.getConsumeThreadCore(),
                liteClientConfig.getConsumeThreadMax(), "EventMesh-client-consume-")
        );
    }

    public LiteConsumer(LiteClientConfig liteClientConfig,
                        ThreadPoolExecutor customExecutor) {
        super(liteClientConfig);
        this.consumeExecutor = customExecutor;
        this.eventMeshClientConfig = liteClientConfig;
    }

    @Override
    public void start() throws Exception {
        Preconditions.checkNotNull(eventMeshClientConfig,
            "EventMeshClientConfig can't be null");
        Preconditions.checkNotNull(consumeExecutor, "consumeExecutor can't be null");
        log.info("LiteConsumer starting");
        super.start();
        started.compareAndSet(false, true);
        log.info("LiteConsumer started");
    }

    @Override
    public void shutdown() throws Exception {
        log.info("LiteConsumer shutting down");
        super.shutdown();
        if (consumeExecutor != null) {
            consumeExecutor.shutdown();
        }
        scheduler.shutdown();
        started.compareAndSet(true, false);
        log.info("LiteConsumer shutdown");
    }

    /**
     * When receive message will callback the url.
     *
     * @param topicList topic that be subscribed
     * @param url       url will be trigger
     * @return true if subscribe success
     * @throws Exception
     */
    public boolean subscribe(List<SubscriptionItem> topicList, String url) throws Exception {
        subscription.addAll(topicList);
        if (!started.get()) {
            start();
        }

        RequestParam subscribeParam = generateSubscribeRequestParam(topicList, url);

        long startTime = System.nanoTime();
        String target = selectEventMesh();
        String subRes = "";

        try (CloseableHttpClient httpClient = setHttpClient()) {
            subRes = HttpUtil.post(httpClient, target, subscribeParam);
        }

        if (log.isDebugEnabled()) {
            log.debug(
                "subscribe message by await, targetEventMesh:{}, cost:{}ms, subscribeParam:{}, "
                    + "rtn:{}", target, (System.nanoTime() - startTime) / 1000000,
                JsonUtils.serialize(subscribeParam), subRes);
        }

        EventMeshRetObj ret = JsonUtils.deserialize(subRes, EventMeshRetObj.class);

        if (ret.getRetCode() == EventMeshRetCode.SUCCESS.getRetCode()) {
            // todo: remove return result
            return true;
        } else {
            throw new EventMeshException(ret.getRetCode(), ret.getRetMsg());
        }

    }

    private RequestParam generateSubscribeRequestParam(List<SubscriptionItem> topicList,
                                                       String url) {
        RequestParam requestParam = new RequestParam(HttpMethod.POST);
        requestParam
            .addHeader(ProtocolKey.REQUEST_CODE, RequestCode.SUBSCRIBE.getRequestCode())
            .addHeader(ProtocolKey.ClientInstanceKey.ENV, eventMeshClientConfig.getEnv())
            .addHeader(ProtocolKey.ClientInstanceKey.IDC, eventMeshClientConfig.getIdc())
            .addHeader(ProtocolKey.ClientInstanceKey.IP, eventMeshClientConfig.getIp())
            .addHeader(ProtocolKey.ClientInstanceKey.PID, eventMeshClientConfig.getPid())
            .addHeader(ProtocolKey.ClientInstanceKey.SYS, eventMeshClientConfig.getSys())
            .addHeader(ProtocolKey.ClientInstanceKey.USERNAME, eventMeshClientConfig.getUserName())
            .addHeader(ProtocolKey.ClientInstanceKey.PASSWD, eventMeshClientConfig.getPassword())
            .addHeader(ProtocolKey.VERSION, ProtocolVersion.V1.getVersion())
            .addHeader(ProtocolKey.LANGUAGE, Constants.LANGUAGE_JAVA)
            .setTimeout(Constants.DEFAULT_HTTP_TIME_OUT)
            .addBody(SubscribeRequestBody.TOPIC, JsonUtils.serialize(topicList))
            .addBody(SubscribeRequestBody.CONSUMERGROUP, eventMeshClientConfig.getConsumerGroup())
            .addBody(SubscribeRequestBody.URL, url);
        return requestParam;
    }

    private RequestParam generateHeartBeatRequestParam(List<SubscriptionItem> topics, String url) {
        List<HeartbeatRequestBody.HeartbeatEntity> heartbeatEntities = new ArrayList<>();
        for (SubscriptionItem item : topics) {
            HeartbeatRequestBody.HeartbeatEntity heartbeatEntity =
                new HeartbeatRequestBody.HeartbeatEntity();
            heartbeatEntity.topic = item.getTopic();
            heartbeatEntity.url = url;
            heartbeatEntities.add(heartbeatEntity);
        }

        RequestParam requestParam = new RequestParam(HttpMethod.POST);
        requestParam
            .addHeader(ProtocolKey.REQUEST_CODE, RequestCode.HEARTBEAT.getRequestCode())
            .addHeader(ProtocolKey.ClientInstanceKey.ENV, eventMeshClientConfig.getEnv())
            .addHeader(ProtocolKey.ClientInstanceKey.IDC, eventMeshClientConfig.getIdc())
            .addHeader(ProtocolKey.ClientInstanceKey.IP, eventMeshClientConfig.getIp())
            .addHeader(ProtocolKey.ClientInstanceKey.PID, eventMeshClientConfig.getPid())
            .addHeader(ProtocolKey.ClientInstanceKey.SYS, eventMeshClientConfig.getSys())
            .addHeader(ProtocolKey.ClientInstanceKey.USERNAME, eventMeshClientConfig.getUserName())
            .addHeader(ProtocolKey.ClientInstanceKey.PASSWD, eventMeshClientConfig.getPassword())
            .addHeader(ProtocolKey.VERSION, ProtocolVersion.V1.getVersion())
            .addHeader(ProtocolKey.LANGUAGE, Constants.LANGUAGE_JAVA)
            .setTimeout(Constants.DEFAULT_HTTP_TIME_OUT)
            .addBody(HeartbeatRequestBody.CLIENTTYPE, ClientType.SUB.name())
            .addBody(HeartbeatRequestBody.CONSUMERGROUP, eventMeshClientConfig.getConsumerGroup())
            .addBody(HeartbeatRequestBody.HEARTBEATENTITIES,
                JsonUtils.serialize(heartbeatEntities));
        return requestParam;
    }

    public void heartBeat(List<SubscriptionItem> topicList, String url) throws Exception {
        scheduler.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                try {
                    if (!started.get()) {
                        start();
                    }
                    RequestParam requestParam = generateHeartBeatRequestParam(topicList, url);

                    long startTime = System.nanoTime();
                    String target = selectEventMesh();
                    String res = "";

                    try (CloseableHttpClient httpClient = setHttpClient()) {
                        res = HttpUtil.post(httpClient, target, requestParam);
                    }

                    if (log.isDebugEnabled()) {
                        log.debug(
                            "heartBeat message by await, targetEventMesh:{}, cost:{}ms, rtn:{}",
                            target, (System.nanoTime() - startTime) / 1000000, res);
                    }

                    EventMeshRetObj ret = JsonUtils.deserialize(res, EventMeshRetObj.class);

                    if (ret.getRetCode() != EventMeshRetCode.SUCCESS.getRetCode()) {
                        throw new EventMeshException(ret.getRetCode(), ret.getRetMsg());
                    }
                } catch (Exception e) {
                    log.error("send heartBeat error", e);
                }
            }
        }, EventMeshCommon.HEARTBEAT, EventMeshCommon.HEARTBEAT, TimeUnit.MILLISECONDS);
    }

    public boolean unsubscribe(List<String> topicList, String url) throws Exception {
        Set<String> unSub = new HashSet<>(topicList);
        subscription.removeIf(item -> unSub.contains(item.getTopic()));

        RequestParam unSubscribeParam = generateUnSubscribeRequestParam(topicList, url);

        long startTime = System.nanoTime();
        String target = selectEventMesh();
        String unSubRes = "";

        try (CloseableHttpClient httpClient = setHttpClient()) {
            unSubRes = HttpUtil.post(httpClient, target, unSubscribeParam);
        }

        if (log.isDebugEnabled()) {
            log.debug(
                "unSubscribe message by await, targetEventMesh:{}, cost:{}ms, unSubscribeParam:{}, "
                    + "rtn:{}", target, (System.nanoTime() - startTime) / 1000000,
                JsonUtils.serialize(unSubscribeParam), unSubRes);
        }

        EventMeshRetObj ret = JsonUtils.deserialize(unSubRes, EventMeshRetObj.class);

        if (ret.getRetCode() == EventMeshRetCode.SUCCESS.getRetCode()) {
            return true;
        } else {
            throw new EventMeshException(ret.getRetCode(), ret.getRetMsg());
        }
    }

    private RequestParam generateUnSubscribeRequestParam(List<String> topicList, String url) {
        RequestParam requestParam = new RequestParam(HttpMethod.POST);
        requestParam
            .addHeader(ProtocolKey.REQUEST_CODE, RequestCode.UNSUBSCRIBE.getRequestCode())
            .addHeader(ProtocolKey.ClientInstanceKey.ENV, eventMeshClientConfig.getEnv())
            .addHeader(ProtocolKey.ClientInstanceKey.IDC, eventMeshClientConfig.getIdc())
            .addHeader(ProtocolKey.ClientInstanceKey.IP, eventMeshClientConfig.getIp())
            .addHeader(ProtocolKey.ClientInstanceKey.PID, eventMeshClientConfig.getPid())
            .addHeader(ProtocolKey.ClientInstanceKey.SYS, eventMeshClientConfig.getSys())
            .addHeader(ProtocolKey.ClientInstanceKey.USERNAME, eventMeshClientConfig.getUserName())
            .addHeader(ProtocolKey.ClientInstanceKey.PASSWD, eventMeshClientConfig.getPassword())
            .addHeader(ProtocolKey.VERSION, ProtocolVersion.V1.getVersion())
            .addHeader(ProtocolKey.LANGUAGE, Constants.LANGUAGE_JAVA)
            .setTimeout(Constants.DEFAULT_HTTP_TIME_OUT)
            .addBody(UnSubscribeRequestBody.TOPIC, JsonUtils.serialize(topicList))
            .addBody(UnSubscribeRequestBody.CONSUMERGROUP, eventMeshClientConfig.getConsumerGroup())
            .addBody(UnSubscribeRequestBody.URL, url);
        return requestParam;
    }

    public String selectEventMesh() {
        if (liteClientConfig.isUseTls()) {
            return Constants.HTTPS_PROTOCOL_PREFIX + eventMeshServerSelector.select();
        } else {
            return Constants.HTTP_PROTOCOL_PREFIX + eventMeshServerSelector.select();
        }
    }
}
