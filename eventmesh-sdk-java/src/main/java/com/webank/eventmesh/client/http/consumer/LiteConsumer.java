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

package com.webank.eventmesh.client.http.consumer;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.webank.eventmesh.client.http.AbstractLiteClient;
import com.webank.eventmesh.client.http.ProxyRetObj;
import com.webank.eventmesh.client.http.RemotingServer;
import com.webank.eventmesh.client.http.conf.LiteClientConfig;
import com.webank.eventmesh.client.http.consumer.listener.LiteMessageListener;
import com.webank.eventmesh.client.http.http.HttpUtil;
import com.webank.eventmesh.client.http.http.RequestParam;
import com.webank.eventmesh.client.tcp.common.MessageUtils;
import com.webank.eventmesh.client.tcp.common.WemqAccessCommon;
import com.webank.eventmesh.client.tcp.common.WemqAccessThreadFactoryImpl;
import com.webank.eventmesh.client.tcp.impl.SimpleSubClientImpl;
import com.webank.eventmesh.common.Constants;
import com.webank.eventmesh.common.LiteMessage;
import com.webank.eventmesh.common.ProxyException;
import com.webank.eventmesh.common.ThreadPoolFactory;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.webank.eventmesh.common.protocol.http.body.client.HeartbeatRequestBody;
import com.webank.eventmesh.common.protocol.http.body.client.SubscribeRequestBody;
import com.webank.eventmesh.common.protocol.http.body.message.SendMessageRequestBody;
import com.webank.eventmesh.common.protocol.http.body.message.SendMessageResponseBody;
import com.webank.eventmesh.common.protocol.http.common.*;
import com.webank.eventmesh.common.protocol.http.header.client.HeartbeatRequestHeader;
import com.webank.eventmesh.common.protocol.tcp.Package;
import io.netty.handler.codec.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class LiteConsumer extends AbstractLiteClient {

    public Logger logger = LoggerFactory.getLogger(LiteConsumer.class);

    private RemotingServer remotingServer;

    private ThreadPoolExecutor consumeExecutor;

    private static CloseableHttpClient httpClient = HttpClients.createDefault();

    protected LiteClientConfig weMQProxyClientConfig;

    private List<String> subscription = Lists.newArrayList();

    private LiteMessageListener messageListener;

    protected static final ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(4, new WemqAccessThreadFactoryImpl("TCPClientScheduler", true));

    public LiteConsumer(LiteClientConfig liteClientConfig) throws Exception {
        super(liteClientConfig);
        this.consumeExecutor = ThreadPoolFactory.createThreadPoolExecutor(liteClientConfig.getConsumeThreadCore(),
                liteClientConfig.getConsumeThreadMax(), "proxy-client-consume-");
        this.weMQProxyClientConfig = liteClientConfig;
//        this.remotingServer = new RemotingServer(10106, consumeExecutor);
//        this.remotingServer.init();
    }

    public LiteConsumer(LiteClientConfig liteClientConfig,
                        ThreadPoolExecutor customExecutor) {
        super(liteClientConfig);
        this.consumeExecutor = customExecutor;
        this.weMQProxyClientConfig = liteClientConfig;
//        this.remotingServer = new RemotingServer(this.consumeExecutor);
    }

    private AtomicBoolean started = new AtomicBoolean(Boolean.FALSE);

    @Override
    public void start() throws Exception {
        Preconditions.checkState(weMQProxyClientConfig != null, "weMQProxyClientConfig can't be null");
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
        httpClient.close();
        started.compareAndSet(true, false);
        logger.info("LiteConsumer shutdown");
    }

    public boolean subscribe(List<String> topicList, String url) throws Exception {
        subscription.addAll(topicList);
        if(!started.get()) {
            start();
        }

        RequestParam heartBeatParam = generateHeartBeatRequestParam(topicList, url);
        RequestParam subscribeParam = generateSubscribeRequestParam(topicList, url);

        long startTime = System.currentTimeMillis();
        String target = selectProxy();
        String subRes = "";
        String heartRes = "";
        try {
            heartRes = HttpUtil.post(httpClient, target, heartBeatParam);
            subRes = HttpUtil.post(httpClient, target, subscribeParam);
        } catch (Exception ex) {
            throw new ProxyException(ex);
        }

        if(logger.isDebugEnabled()) {
            logger.debug("subscribe message by await, targetProxy:{}, cost:{}ms, subscribeParam:{}, rtn:{}", target, System.currentTimeMillis() - startTime, JSON.toJSONString(subscribeParam), subRes);
        }

        ProxyRetObj ret = JSON.parseObject(subRes, ProxyRetObj.class);

        if (ret.getRetCode() == ProxyRetCode.SUCCESS.getRetCode()) {
            return Boolean.TRUE;
        } else {
            throw new ProxyException(ret.getRetCode(), ret.getRetMsg());
        }

    }

    private RequestParam generateSubscribeRequestParam(List<String> topicList, String url) {
//        final LiteMessage liteMessage = new LiteMessage();
//        liteMessage.setBizSeqNo(RandomStringUtils.randomNumeric(30))
//                .setContent("subscribe message")
//                .setUniqueId(RandomStringUtils.randomNumeric(30));
        RequestParam requestParam = new RequestParam(HttpMethod.POST);
        requestParam.addHeader(ProtocolKey.REQUEST_CODE, String.valueOf(RequestCode.SUBSCRIBE.getRequestCode()))
                .addHeader(ProtocolKey.ClientInstanceKey.ENV, weMQProxyClientConfig.getEnv())
                .addHeader(ProtocolKey.ClientInstanceKey.REGION, weMQProxyClientConfig.getRegion())
                .addHeader(ProtocolKey.ClientInstanceKey.IDC, weMQProxyClientConfig.getIdc())
                .addHeader(ProtocolKey.ClientInstanceKey.DCN, weMQProxyClientConfig.getDcn())
                .addHeader(ProtocolKey.ClientInstanceKey.IP, weMQProxyClientConfig.getIp())
                .addHeader(ProtocolKey.ClientInstanceKey.PID, weMQProxyClientConfig.getPid())
                .addHeader(ProtocolKey.ClientInstanceKey.SYS, weMQProxyClientConfig.getSys())
                .addHeader(ProtocolKey.ClientInstanceKey.USERNAME, weMQProxyClientConfig.getUserName())
                .addHeader(ProtocolKey.ClientInstanceKey.PASSWD, weMQProxyClientConfig.getPassword())
                .addHeader(ProtocolKey.VERSION, ProtocolVersion.V1.getVersion())
                .addHeader(ProtocolKey.LANGUAGE, Constants.LANGUAGE_JAVA)
                .setTimeout(Constants.DEFAULT_HTTP_TIME_OUT)
                .addBody(SubscribeRequestBody.TOPIC, JSONObject.toJSONString(topicList))
                .addBody(SubscribeRequestBody.URL, url);
        return requestParam;
    }

    private RequestParam generateHeartBeatRequestParam(List<String> topics, String url) {
        List<HeartbeatRequestBody.HeartbeatEntity> heartbeatEntities = new ArrayList<>();
        for (String topic : topics){
            HeartbeatRequestBody.HeartbeatEntity heartbeatEntity = new HeartbeatRequestBody.HeartbeatEntity();
            heartbeatEntity.topic = topic;
            heartbeatEntity.url = url;
            heartbeatEntities.add(heartbeatEntity);
        }

        RequestParam requestParam = new RequestParam(HttpMethod.POST);
        requestParam.addHeader(ProtocolKey.REQUEST_CODE, String.valueOf(RequestCode.HEARTBEAT.getRequestCode()))
                .addHeader(ProtocolKey.ClientInstanceKey.ENV, weMQProxyClientConfig.getEnv())
                .addHeader(ProtocolKey.ClientInstanceKey.REGION, weMQProxyClientConfig.getRegion())
                .addHeader(ProtocolKey.ClientInstanceKey.IDC, weMQProxyClientConfig.getIdc())
                .addHeader(ProtocolKey.ClientInstanceKey.DCN, weMQProxyClientConfig.getDcn())
                .addHeader(ProtocolKey.ClientInstanceKey.IP, weMQProxyClientConfig.getIp())
                .addHeader(ProtocolKey.ClientInstanceKey.PID, weMQProxyClientConfig.getPid())
                .addHeader(ProtocolKey.ClientInstanceKey.SYS, weMQProxyClientConfig.getSys())
                .addHeader(ProtocolKey.ClientInstanceKey.USERNAME, weMQProxyClientConfig.getUserName())
                .addHeader(ProtocolKey.ClientInstanceKey.PASSWD, weMQProxyClientConfig.getPassword())
                .addHeader(ProtocolKey.VERSION, ProtocolVersion.V1.getVersion())
                .addHeader(ProtocolKey.LANGUAGE, Constants.LANGUAGE_JAVA)
                .setTimeout(Constants.DEFAULT_HTTP_TIME_OUT)
                .addBody(HeartbeatRequestBody.CLIENTTYPE, ClientType.SUB.name())
                .addBody(HeartbeatRequestBody.HEARTBEATENTITIES, JSON.toJSONString(heartbeatEntities));
        return requestParam;
    }

    public void heartBeat(List<String> topicList, String url) throws Exception {
        scheduler.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                try {
                    if(!started.get()) {
                        start();
                    }
                    RequestParam requestParam = generateHeartBeatRequestParam(topicList, url);

                    long startTime = System.currentTimeMillis();
                    String target = selectProxy();
                    String res = "";
                    try {
                        res = HttpUtil.post(httpClient, target, requestParam);
                    } catch (Exception ex) {
                        throw new ProxyException(ex);
                    }

                    if(logger.isDebugEnabled()) {
                        logger.debug("heartBeat message by await, targetProxy:{}, cost:{}ms, rtn:{}", target, System.currentTimeMillis() - startTime, res);
                    }

                    ProxyRetObj ret = JSON.parseObject(res, ProxyRetObj.class);

                    if (ret.getRetCode() == ProxyRetCode.SUCCESS.getRetCode()) {
                    } else {
                        throw new ProxyException(ret.getRetCode(), ret.getRetMsg());
                    }
                } catch (Exception e) {
                    logger.error("send heartBeat error", e);
                }
            }
        }, WemqAccessCommon.HEATBEAT, WemqAccessCommon.HEATBEAT, TimeUnit.MILLISECONDS);
    }

    public boolean unsubscribe(List<String> topicList, String url) throws ProxyException {
        subscription.removeAll(topicList);
        RequestParam heartBeatParam = generateHeartBeatRequestParam(topicList, url);
        RequestParam unSubscribeParam = generateUnSubscribeRequestParam(topicList, url);

        long startTime = System.currentTimeMillis();
        String target = selectProxy();
        String unSubRes = "";
        String heartRes = "";
        try {
            heartRes = HttpUtil.post(httpClient, target, heartBeatParam);
            unSubRes = HttpUtil.post(httpClient, target, unSubscribeParam);
        } catch (Exception ex) {
            throw new ProxyException(ex);
        }

        if(logger.isDebugEnabled()) {
            logger.debug("unSubscribe message by await, targetProxy:{}, cost:{}ms, unSubscribeParam:{}, rtn:{}", target, System.currentTimeMillis() - startTime, JSON.toJSONString(unSubscribeParam), unSubRes);
        }

        ProxyRetObj ret = JSON.parseObject(unSubRes, ProxyRetObj.class);

        if (ret.getRetCode() == ProxyRetCode.SUCCESS.getRetCode()) {
            return Boolean.TRUE;
        } else {
            throw new ProxyException(ret.getRetCode(), ret.getRetMsg());
        }
    }

    private RequestParam generateUnSubscribeRequestParam(List<String> topicList, String url) {
        RequestParam requestParam = new RequestParam(HttpMethod.POST);
        requestParam.addHeader(ProtocolKey.REQUEST_CODE, String.valueOf(RequestCode.UNSUBSCRIBE.getRequestCode()))
                .addHeader(ProtocolKey.ClientInstanceKey.ENV, weMQProxyClientConfig.getEnv())
                .addHeader(ProtocolKey.ClientInstanceKey.REGION, weMQProxyClientConfig.getRegion())
                .addHeader(ProtocolKey.ClientInstanceKey.IDC, weMQProxyClientConfig.getIdc())
                .addHeader(ProtocolKey.ClientInstanceKey.DCN, weMQProxyClientConfig.getDcn())
                .addHeader(ProtocolKey.ClientInstanceKey.IP, weMQProxyClientConfig.getIp())
                .addHeader(ProtocolKey.ClientInstanceKey.PID, weMQProxyClientConfig.getPid())
                .addHeader(ProtocolKey.ClientInstanceKey.SYS, weMQProxyClientConfig.getSys())
                .addHeader(ProtocolKey.ClientInstanceKey.USERNAME, weMQProxyClientConfig.getUserName())
                .addHeader(ProtocolKey.ClientInstanceKey.PASSWD, weMQProxyClientConfig.getPassword())
                .addHeader(ProtocolKey.VERSION, ProtocolVersion.V1.getVersion())
                .addHeader(ProtocolKey.LANGUAGE, Constants.LANGUAGE_JAVA)
                .setTimeout(Constants.DEFAULT_HTTP_TIME_OUT)
                .addBody(SubscribeRequestBody.TOPIC, JSONObject.toJSONString(topicList))
                .addBody(SubscribeRequestBody.URL, url);
        return requestParam;
    }

    public void registerMessageListener(LiteMessageListener messageListener) throws ProxyException {
        this.messageListener = messageListener;
        remotingServer.registerMessageListener(this.messageListener);
    }

    public String selectProxy() {
        if (CollectionUtils.isEmpty(proxyServerList)) {
            return null;
        }
        if(liteClientConfig.isUseTls()){
            return Constants.HTTPS_PROTOCOL_PREFIX + proxyServerList.get(RandomUtils.nextInt(0, proxyServerList.size()));
        }else{
            return Constants.HTTP_PROTOCOL_PREFIX + proxyServerList.get(RandomUtils.nextInt(0, proxyServerList.size()));
        }
    }
}
