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

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import io.netty.handler.codec.http.HttpMethod;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.eventmesh.client.http.AbstractLiteClient;
import org.apache.eventmesh.client.http.EventMeshRetObj;
import org.apache.eventmesh.client.http.RemotingServer;
import org.apache.eventmesh.client.http.conf.LiteClientConfig;
import org.apache.eventmesh.client.http.consumer.listener.LiteMessageListener;
import org.apache.eventmesh.client.http.http.HttpUtil;
import org.apache.eventmesh.client.http.http.RequestParam;
import org.apache.eventmesh.client.tcp.common.EventMeshCommon;
import org.apache.eventmesh.client.tcp.common.EventMeshThreadFactoryImpl;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LiteConsumer extends AbstractLiteClient {

    public Logger logger = LoggerFactory.getLogger(LiteConsumer.class);

    private RemotingServer remotingServer;

    private ThreadPoolExecutor consumeExecutor;

    protected LiteClientConfig eventMeshClientConfig;

    private List<SubscriptionItem> subscription = Lists.newArrayList();

    private LiteMessageListener messageListener;

    protected final ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(4, new EventMeshThreadFactoryImpl("TCPClientScheduler", true));

    public LiteConsumer(LiteClientConfig liteClientConfig) throws Exception {
        super(liteClientConfig);
        this.consumeExecutor = ThreadPoolFactory.createThreadPoolExecutor(liteClientConfig.getConsumeThreadCore(),
            liteClientConfig.getConsumeThreadMax(), "eventMesh-client-consume-");
        this.eventMeshClientConfig = liteClientConfig;
//        this.remotingServer = new RemotingServer(10106, consumeExecutor);
//        this.remotingServer.init();
    }

    public LiteConsumer(LiteClientConfig liteClientConfig,
        ThreadPoolExecutor customExecutor) {
        super(liteClientConfig);
        this.consumeExecutor = customExecutor;
        this.eventMeshClientConfig = liteClientConfig;
//        this.remotingServer = new RemotingServer(this.consumeExecutor);
    }

    private final AtomicBoolean started = new AtomicBoolean(Boolean.FALSE);

    @Override
    public void start() throws Exception {
        Preconditions.checkState(eventMeshClientConfig != null, "eventMeshClientConfig can't be null");
        Preconditions.checkState(consumeExecutor != null, "consumeExecutor can't be null");
//        Preconditions.checkState(messageListener != null, "messageListener can't be null");
        logger.info("LiteConsumer starting");
        super.start();
        started.compareAndSet(false, true);
        logger.info("LiteConsumer started");
//        this.remotingServer.start();
    }

    @Override
    public void shutdown() throws Exception {
        logger.info("LiteConsumer shutting down");
        super.shutdown();
        if (consumeExecutor != null) {
            consumeExecutor.shutdown();
        }
        scheduler.shutdown();
        started.compareAndSet(true, false);
        logger.info("LiteConsumer shutdown");
    }

    public boolean subscribe(List<SubscriptionItem> topicList, String url) throws Exception {
        subscription.addAll(topicList);
        if (!started.get()) {
            start();
        }

        RequestParam subscribeParam = generateSubscribeRequestParam(topicList, url);

        long startTime = System.currentTimeMillis();
        String target = selectEventMesh();
        String subRes = "";

        try (CloseableHttpClient httpClient = setHttpClient()) {
            subRes = HttpUtil.post(httpClient, target, subscribeParam);
        }

        if (logger.isDebugEnabled()) {
            logger.debug("subscribe message by await, targetEventMesh:{}, cost:{}ms, subscribeParam:{}, rtn:{}",
                target, System.currentTimeMillis() - startTime, JsonUtils.serialize(subscribeParam), subRes);
        }

        EventMeshRetObj ret = JsonUtils.deserialize(subRes, EventMeshRetObj.class);

        if (ret.getRetCode() == EventMeshRetCode.SUCCESS.getRetCode()) {
            return Boolean.TRUE;
        } else {
            throw new EventMeshException(ret.getRetCode(), ret.getRetMsg());
        }

    }

    private RequestParam generateSubscribeRequestParam(List<SubscriptionItem> topicList, String url) {
//        final LiteMessage liteMessage = new LiteMessage();
//        liteMessage.setBizSeqNo(RandomStringUtils.randomNumeric(30))
//                .setContent("subscribe message")
//                .setUniqueId(RandomStringUtils.randomNumeric(30));
        RequestParam requestParam = new RequestParam(HttpMethod.POST);
        requestParam.addHeader(ProtocolKey.REQUEST_CODE, String.valueOf(RequestCode.SUBSCRIBE.getRequestCode()))
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
            HeartbeatRequestBody.HeartbeatEntity heartbeatEntity = new HeartbeatRequestBody.HeartbeatEntity();
            heartbeatEntity.topic = item.getTopic();
            heartbeatEntity.url = url;
            heartbeatEntities.add(heartbeatEntity);
        }

        RequestParam requestParam = new RequestParam(HttpMethod.POST);
        requestParam.addHeader(ProtocolKey.REQUEST_CODE, String.valueOf(RequestCode.HEARTBEAT.getRequestCode()))
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
            .addBody(HeartbeatRequestBody.HEARTBEATENTITIES, JsonUtils.serialize(heartbeatEntities));
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

                    long startTime = System.currentTimeMillis();
                    String target = selectEventMesh();
                    String res = "";

                    try (CloseableHttpClient httpClient = setHttpClient()) {
                        res = HttpUtil.post(httpClient, target, requestParam);
                    }

                    if (logger.isDebugEnabled()) {
                        logger.debug("heartBeat message by await, targetEventMesh:{}, cost:{}ms, rtn:{}", target, System.currentTimeMillis() - startTime, res);
                    }

                    EventMeshRetObj ret = JsonUtils.deserialize(res, EventMeshRetObj.class);

                    if (ret.getRetCode() != EventMeshRetCode.SUCCESS.getRetCode()) {
                        throw new EventMeshException(ret.getRetCode(), ret.getRetMsg());
                    }
                } catch (Exception e) {
                    logger.error("send heartBeat error", e);
                }
            }
        }, EventMeshCommon.HEARTBEAT, EventMeshCommon.HEARTBEAT, TimeUnit.MILLISECONDS);
    }

    public boolean unsubscribe(List<String> topicList, String url) throws Exception {
        Set<String> unSub = new HashSet<>(topicList);
        Iterator<SubscriptionItem> itr = subscription.iterator();
        while (itr.hasNext()) {
            SubscriptionItem item = itr.next();
            if (unSub.contains(item.getTopic())) {
                itr.remove();
            }
        }

        RequestParam unSubscribeParam = generateUnSubscribeRequestParam(topicList, url);

        long startTime = System.currentTimeMillis();
        String target = selectEventMesh();
        String unSubRes = "";

        try (CloseableHttpClient httpClient = setHttpClient()) {
            unSubRes = HttpUtil.post(httpClient, target, unSubscribeParam);
        }

        if (logger.isDebugEnabled()) {
            logger.debug("unSubscribe message by await, targetEventMesh:{}, cost:{}ms, unSubscribeParam:{}, rtn:{}",
                target, System.currentTimeMillis() - startTime, JsonUtils.serialize(unSubscribeParam), unSubRes);
        }

        EventMeshRetObj ret = JsonUtils.deserialize(unSubRes, EventMeshRetObj.class);

        if (ret.getRetCode() == EventMeshRetCode.SUCCESS.getRetCode()) {
            return Boolean.TRUE;
        } else {
            throw new EventMeshException(ret.getRetCode(), ret.getRetMsg());
        }
    }

    private RequestParam generateUnSubscribeRequestParam(List<String> topicList, String url) {
        RequestParam requestParam = new RequestParam(HttpMethod.POST);
        requestParam.addHeader(ProtocolKey.REQUEST_CODE, String.valueOf(RequestCode.UNSUBSCRIBE.getRequestCode()))
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

    public void registerMessageListener(LiteMessageListener messageListener) throws EventMeshException {
        this.messageListener = messageListener;
        remotingServer.registerMessageListener(this.messageListener);
    }

    public String selectEventMesh() {
        if (liteClientConfig.isUseTls()) {
            return Constants.HTTPS_PROTOCOL_PREFIX + eventMeshServerSelector.select();
        } else {
            return Constants.HTTP_PROTOCOL_PREFIX + eventMeshServerSelector.select();
        }
    }
}
