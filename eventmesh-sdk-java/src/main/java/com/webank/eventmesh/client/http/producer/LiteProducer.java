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

package com.webank.eventmesh.client.http.producer;

import com.webank.eventmesh.client.http.AbstractLiteClient;
import com.webank.eventmesh.client.http.ProxyRetObj;
import com.webank.eventmesh.client.http.conf.LiteClientConfig;
import com.webank.eventmesh.client.http.http.HttpUtil;
import com.webank.eventmesh.client.http.http.RequestParam;
import com.webank.eventmesh.common.Constants;
import com.webank.eventmesh.common.LiteMessage;
import com.webank.eventmesh.common.ProxyException;
import com.webank.eventmesh.common.protocol.http.body.message.SendMessageRequestBody;
import com.webank.eventmesh.common.protocol.http.body.message.SendMessageResponseBody;
import com.webank.eventmesh.common.protocol.http.common.ProtocolKey;
import com.webank.eventmesh.common.protocol.http.common.ProtocolVersion;
import com.webank.eventmesh.common.protocol.http.common.ProxyRetCode;
import com.webank.eventmesh.common.protocol.http.common.RequestCode;
import com.alibaba.fastjson.JSON;
import com.google.common.base.Preconditions;
import io.netty.handler.codec.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class LiteProducer extends AbstractLiteClient {

    public Logger logger = LoggerFactory.getLogger(LiteProducer.class);

    private static CloseableHttpClient httpClient = HttpClients.createDefault();

    public LiteProducer(LiteClientConfig liteClientConfig) {
        super(liteClientConfig);
    }

    private AtomicBoolean started = new AtomicBoolean(Boolean.FALSE);

    public void start() throws ProxyException {
        Preconditions.checkState(liteClientConfig != null, "liteClientConfig can't be null");
        Preconditions.checkState(liteClientConfig.getLiteProxyAddr() != null, "liteClientConfig.liteServerAddr can't be null");
        if(started.get()) {
            return;
        }
        logger.info("LiteProducer starting");
        super.start();
        started.compareAndSet(false, true);
        logger.info("LiteProducer started");
    }

    public void shutdown() throws Exception {
        if(!started.get()) {
            return;
        }
        logger.info("LiteProducer shutting down");
        super.shutdown();
        httpClient.close();
        started.compareAndSet(true, false);
        logger.info("LiteProducer shutdown");
    }

    public AtomicBoolean getStarted() {
        return started;
    }

    public boolean publish(LiteMessage message) throws ProxyException {
        if (!started.get()) {
            start();
        }
        Preconditions.checkState(StringUtils.isNotBlank(message.getTopic()),
                "proxyMessage[topic] invalid");
        Preconditions.checkState(StringUtils.isNotBlank(message.getContent()),
                "proxyMessage[content] invalid");
        RequestParam requestParam = new RequestParam(HttpMethod.POST);
        requestParam.addHeader(ProtocolKey.REQUEST_CODE, String.valueOf(RequestCode.MSG_SEND_ASYNC.getRequestCode()))
                .addHeader(ProtocolKey.ClientInstanceKey.ENV, liteClientConfig.getEnv())
                .addHeader(ProtocolKey.ClientInstanceKey.REGION, liteClientConfig.getRegion())
                .addHeader(ProtocolKey.ClientInstanceKey.IDC, liteClientConfig.getIdc())
                .addHeader(ProtocolKey.ClientInstanceKey.DCN, liteClientConfig.getDcn())
                .addHeader(ProtocolKey.ClientInstanceKey.IP, liteClientConfig.getIp())
                .addHeader(ProtocolKey.ClientInstanceKey.PID, liteClientConfig.getPid())
                .addHeader(ProtocolKey.ClientInstanceKey.SYS, liteClientConfig.getSys())
                .addHeader(ProtocolKey.ClientInstanceKey.USERNAME, liteClientConfig.getUserName())
                .addHeader(ProtocolKey.ClientInstanceKey.PASSWD, liteClientConfig.getPassword())
                .addHeader(ProtocolKey.VERSION, ProtocolVersion.V1.getVersion())
                .addHeader(ProtocolKey.LANGUAGE, Constants.LANGUAGE_JAVA)
                .setTimeout(Constants.DEFAULT_HTTP_TIME_OUT)
                .addBody(SendMessageRequestBody.TOPIC, message.getTopic())
                .addBody(SendMessageRequestBody.CONTENT, message.getContent())
                .addBody(SendMessageRequestBody.TTL, message.getPropKey(Constants.PROXY_MESSAGE_CONST_TTL))
                .addBody(SendMessageRequestBody.BIZSEQNO, message.getBizSeqNo())
                .addBody(SendMessageRequestBody.UNIQUEID, message.getUniqueId());

        long startTime = System.currentTimeMillis();
        String targetRegion =
                MapUtils.getObject(message.getProp(), Constants.TARGET_PROXY_REGION, Constants.CONSTANTS_DEFAULT_REGION_KEY);
        String target = getCurrProxy(targetRegion);
        String res = "";
        try {
            res = HttpUtil.post(httpClient, target, requestParam);
//            res = HttpUtil.post(httpClient, getAvailablesForwardAgent(), target, requestParam);
        } catch (Exception ex) {
            throw new ProxyException(ex);
        }

        if(logger.isDebugEnabled()) {
            logger.debug("publish async message, targetProxy:{}, cost:{}ms, message:{}, rtn:{}",
                    target, System.currentTimeMillis() - startTime, message, res);
        }

        ProxyRetObj ret = JSON.parseObject(res, ProxyRetObj.class);

        if (ret.getRetCode() == ProxyRetCode.SUCCESS.getRetCode()) {
            return Boolean.TRUE;
        } else {
            throw new ProxyException(ret.getRetCode(), ret.getRetMsg());
        }
    }

    public String getCurrProxy(String targetRegion) {
        List<String> availableServers = getAvailableServers(targetRegion);
        if (CollectionUtils.isEmpty(availableServers)) {
            return null;
        }
        return Constants.HTTP_PROTOCOL_PREFIX + availableServers.get(RandomUtils.nextInt(0, availableServers.size()));
    }

    public LiteMessage request(LiteMessage message, long timeout) throws ProxyException {
        if(!started.get()) {
            start();
        }
        Preconditions.checkState(StringUtils.isNotBlank(message.getTopic()),
                "proxyMessage[topic] invalid");
        Preconditions.checkState(StringUtils.isNotBlank(message.getContent()),
                "proxyMessage[content] invalid");
        RequestParam requestParam = new RequestParam(HttpMethod.POST);
        requestParam.addHeader(ProtocolKey.REQUEST_CODE, String.valueOf(RequestCode.MSG_SEND_SYNC.getRequestCode()))
                .addHeader(ProtocolKey.ClientInstanceKey.ENV, liteClientConfig.getEnv())
                .addHeader(ProtocolKey.ClientInstanceKey.REGION, liteClientConfig.getRegion())
                .addHeader(ProtocolKey.ClientInstanceKey.IDC, liteClientConfig.getIdc())
                .addHeader(ProtocolKey.ClientInstanceKey.DCN, liteClientConfig.getDcn())
                .addHeader(ProtocolKey.ClientInstanceKey.IP, liteClientConfig.getIp())
                .addHeader(ProtocolKey.ClientInstanceKey.PID, liteClientConfig.getPid())
                .addHeader(ProtocolKey.ClientInstanceKey.SYS, liteClientConfig.getSys())
                .addHeader(ProtocolKey.ClientInstanceKey.USERNAME, liteClientConfig.getUserName())
                .addHeader(ProtocolKey.ClientInstanceKey.PASSWD, liteClientConfig.getPassword())
                .addHeader(ProtocolKey.VERSION, ProtocolVersion.V1.getVersion())
                .addHeader(ProtocolKey.LANGUAGE, Constants.LANGUAGE_JAVA)
                .setTimeout(timeout)
                .addBody(SendMessageRequestBody.TOPIC, message.getTopic())
                .addBody(SendMessageRequestBody.CONTENT, message.getContent())
                .addBody(SendMessageRequestBody.TTL, String.valueOf(timeout))
                .addBody(SendMessageRequestBody.BIZSEQNO, message.getBizSeqNo())
                .addBody(SendMessageRequestBody.UNIQUEID, message.getUniqueId());

        long startTime = System.currentTimeMillis();
        String targetRegion =
                MapUtils.getObject(message.getProp(), Constants.TARGET_PROXY_REGION, Constants.CONSTANTS_DEFAULT_REGION_KEY);
        String target = getCurrProxy(targetRegion);
        String res = "";
        try {
            //res = HttpUtil.post(httpClient, getAvailablesForwardAgent(), target, requestParam);
            res = HttpUtil.post(httpClient, target, requestParam);
        } catch (Exception ex) {
            throw new ProxyException(ex);
        }

        if(logger.isDebugEnabled()) {
            logger.debug("publish sync message by await, targetProxy:{}, cost:{}ms, message:{}, rtn:{}", target, System.currentTimeMillis() - startTime, message, res);
        }

        ProxyRetObj ret = JSON.parseObject(res, ProxyRetObj.class);
        if (ret.getRetCode() == ProxyRetCode.SUCCESS.getRetCode()) {
            LiteMessage proxyMessage = new LiteMessage();
            SendMessageResponseBody.ReplyMessage replyMessage =
                    JSON.parseObject(ret.getRetMsg(), SendMessageResponseBody.ReplyMessage.class);
            proxyMessage.setContent(replyMessage.body).setProp(replyMessage.properties)
                    .setTopic(replyMessage.topic);
            return proxyMessage;
        }

        return null;
    }

    public void request(LiteMessage message, RRCallback rrCallback, long timeout) throws ProxyException {
        if(!started.get()) {
            start();
        }
        Preconditions.checkState(StringUtils.isNotBlank(message.getTopic()),
                "proxyMessage[topic] invalid");
        Preconditions.checkState(StringUtils.isNotBlank(message.getContent()),
                "proxyMessage[content] invalid");
        Preconditions.checkState(ObjectUtils.allNotNull(rrCallback),
                "rrCallback invalid");
        RequestParam requestParam = new RequestParam(HttpMethod.POST);
        requestParam.addHeader(ProtocolKey.REQUEST_CODE, String.valueOf(RequestCode.MSG_SEND_SYNC.getRequestCode()))
                .addHeader(ProtocolKey.ClientInstanceKey.ENV, liteClientConfig.getEnv())
                .addHeader(ProtocolKey.ClientInstanceKey.REGION, liteClientConfig.getRegion())
                .addHeader(ProtocolKey.ClientInstanceKey.IDC, liteClientConfig.getIdc())
                .addHeader(ProtocolKey.ClientInstanceKey.DCN, liteClientConfig.getDcn())
                .addHeader(ProtocolKey.ClientInstanceKey.IP, liteClientConfig.getIp())
                .addHeader(ProtocolKey.ClientInstanceKey.PID, liteClientConfig.getPid())
                .addHeader(ProtocolKey.ClientInstanceKey.SYS, liteClientConfig.getSys())
                .addHeader(ProtocolKey.ClientInstanceKey.USERNAME, liteClientConfig.getUserName())
                .addHeader(ProtocolKey.ClientInstanceKey.PASSWD, liteClientConfig.getPassword())
                .addHeader(ProtocolKey.VERSION, ProtocolVersion.V1.getVersion())
                .addHeader(ProtocolKey.LANGUAGE, Constants.LANGUAGE_JAVA)
                .setTimeout(timeout)
                .addBody(SendMessageRequestBody.TOPIC, message.getTopic())
                .addBody(SendMessageRequestBody.CONTENT, message.getContent())
                .addBody(SendMessageRequestBody.TTL, String.valueOf(timeout))
                .addBody(SendMessageRequestBody.BIZSEQNO, message.getBizSeqNo())
                .addBody(SendMessageRequestBody.UNIQUEID, message.getUniqueId());

        long startTime = System.currentTimeMillis();
        String targetRegion =
                MapUtils.getObject(message.getProp(), Constants.TARGET_PROXY_REGION, Constants.CONSTANTS_DEFAULT_REGION_KEY);
        String target = getCurrProxy(targetRegion);
        try {
//            HttpUtil.post(httpClient, getAvailablesForwardAgent(), target, requestParam, new RRCallbackResponseHandlerAdapter(message, rrCallback, timeout));
            HttpUtil.post(httpClient, null, target, requestParam, new RRCallbackResponseHandlerAdapter(message, rrCallback, timeout));
        } catch (Exception ex) {
            throw new ProxyException(ex);
        }

        if(logger.isDebugEnabled()) {
            logger.debug("publish sync message by async, target:{}, cost:{}, message:{}", target, System.currentTimeMillis() - startTime, message);
        }
    }
}
