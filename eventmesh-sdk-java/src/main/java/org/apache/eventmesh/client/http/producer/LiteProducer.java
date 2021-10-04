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

package org.apache.eventmesh.client.http.producer;

import com.google.common.base.Preconditions;
import io.netty.handler.codec.http.HttpMethod;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.eventmesh.client.http.AbstractLiteClient;
import org.apache.eventmesh.client.http.EventMeshRetObj;
import org.apache.eventmesh.client.http.conf.LiteClientConfig;
import org.apache.eventmesh.client.http.http.HttpUtil;
import org.apache.eventmesh.client.http.http.RequestParam;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.EventMeshException;
import org.apache.eventmesh.common.LiteMessage;
import org.apache.eventmesh.common.protocol.http.body.message.SendMessageRequestBody;
import org.apache.eventmesh.common.protocol.http.body.message.SendMessageResponseBody;
import org.apache.eventmesh.common.protocol.http.common.EventMeshRetCode;
import org.apache.eventmesh.common.protocol.http.common.ProtocolKey;
import org.apache.eventmesh.common.protocol.http.common.ProtocolVersion;
import org.apache.eventmesh.common.protocol.http.common.RequestCode;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.http.impl.client.CloseableHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

public class LiteProducer extends AbstractLiteClient {

    public Logger logger = LoggerFactory.getLogger(LiteProducer.class);

    public LiteProducer(LiteClientConfig liteClientConfig) {
        super(liteClientConfig);
    }

    private AtomicBoolean started = new AtomicBoolean(Boolean.FALSE);

    @Override
    public void start() throws Exception {
        Preconditions.checkState(liteClientConfig != null, "liteClientConfig can't be null");
        Preconditions.checkState(liteClientConfig.getLiteEventMeshAddr() != null, "liteClientConfig.liteServerAddr can't be null");
        if (started.get()) {
            return;
        }
        logger.info("LiteProducer starting");
        super.start();
        started.compareAndSet(false, true);
        logger.info("LiteProducer started");
    }

    @Override
    public void shutdown() throws Exception {
        if (!started.get()) {
            return;
        }
        logger.info("LiteProducer shutting down");
        super.shutdown();
        started.compareAndSet(true, false);
        logger.info("LiteProducer shutdown");
    }

    public AtomicBoolean getStarted() {
        return started;
    }

    public boolean publish(LiteMessage message) throws Exception {
        if (!started.get()) {
            start();
        }
        Preconditions.checkState(StringUtils.isNotBlank(message.getTopic()),
                "eventMeshMessage[topic] invalid");
        Preconditions.checkState(StringUtils.isNotBlank(message.getContent()),
                "eventMeshMessage[content] invalid");
        RequestParam requestParam = new RequestParam(HttpMethod.POST);
        requestParam.addHeader(ProtocolKey.REQUEST_CODE, String.valueOf(RequestCode.MSG_SEND_ASYNC.getRequestCode()))
                .addHeader(ProtocolKey.ClientInstanceKey.ENV, liteClientConfig.getEnv())
                .addHeader(ProtocolKey.ClientInstanceKey.IDC, liteClientConfig.getIdc())
                .addHeader(ProtocolKey.ClientInstanceKey.IP, liteClientConfig.getIp())
                .addHeader(ProtocolKey.ClientInstanceKey.PID, liteClientConfig.getPid())
                .addHeader(ProtocolKey.ClientInstanceKey.SYS, liteClientConfig.getSys())
                .addHeader(ProtocolKey.ClientInstanceKey.USERNAME, liteClientConfig.getUserName())
                .addHeader(ProtocolKey.ClientInstanceKey.PASSWD, liteClientConfig.getPassword())
                .addHeader(ProtocolKey.VERSION, ProtocolVersion.V1.getVersion())
                .addHeader(ProtocolKey.LANGUAGE, Constants.LANGUAGE_JAVA)
                .setTimeout(Constants.DEFAULT_HTTP_TIME_OUT)
                .addBody(SendMessageRequestBody.PRODUCERGROUP, liteClientConfig.getProducerGroup())
                .addBody(SendMessageRequestBody.TOPIC, message.getTopic())
                .addBody(SendMessageRequestBody.CONTENT, message.getContent())
                .addBody(SendMessageRequestBody.TTL, message.getPropKey(Constants.EVENTMESH_MESSAGE_CONST_TTL))
                .addBody(SendMessageRequestBody.BIZSEQNO, message.getBizSeqNo())
                .addBody(SendMessageRequestBody.UNIQUEID, message.getUniqueId());

        long startTime = System.currentTimeMillis();
        String target = selectEventMesh();
        String res = "";

        try (CloseableHttpClient httpClient = setHttpClient()) {
            res = HttpUtil.post(httpClient, target, requestParam);
        }

        if (logger.isDebugEnabled()) {
            logger.debug("publish async message, targetEventMesh:{}, cost:{}ms, message:{}, rtn:{}",
                    target, System.currentTimeMillis() - startTime, message, res);
        }

        EventMeshRetObj ret = JsonUtils.deserialize(res, EventMeshRetObj.class);

        if (ret.getRetCode() == EventMeshRetCode.SUCCESS.getRetCode()) {
            return Boolean.TRUE;
        } else {
            throw new EventMeshException(ret.getRetCode(), ret.getRetMsg());
        }
    }

    public String selectEventMesh() {
        if (liteClientConfig.isUseTls()) {
            return Constants.HTTPS_PROTOCOL_PREFIX + eventMeshServerSelector.select();
        } else {
            return Constants.HTTP_PROTOCOL_PREFIX + eventMeshServerSelector.select();
        }
    }

    public LiteMessage request(LiteMessage message, long timeout) throws Exception {
        if (!started.get()) {
            start();
        }
        Preconditions.checkState(StringUtils.isNotBlank(message.getTopic()),
                "eventMeshMessage[topic] invalid");
        Preconditions.checkState(StringUtils.isNotBlank(message.getContent()),
                "eventMeshMessage[content] invalid");
        RequestParam requestParam = new RequestParam(HttpMethod.POST);
        requestParam.addHeader(ProtocolKey.REQUEST_CODE, String.valueOf(RequestCode.MSG_SEND_SYNC.getRequestCode()))
                .addHeader(ProtocolKey.ClientInstanceKey.ENV, liteClientConfig.getEnv())
                .addHeader(ProtocolKey.ClientInstanceKey.IDC, liteClientConfig.getIdc())
                .addHeader(ProtocolKey.ClientInstanceKey.IP, liteClientConfig.getIp())
                .addHeader(ProtocolKey.ClientInstanceKey.PID, liteClientConfig.getPid())
                .addHeader(ProtocolKey.ClientInstanceKey.SYS, liteClientConfig.getSys())
                .addHeader(ProtocolKey.ClientInstanceKey.USERNAME, liteClientConfig.getUserName())
                .addHeader(ProtocolKey.ClientInstanceKey.PASSWD, liteClientConfig.getPassword())
                .addHeader(ProtocolKey.VERSION, ProtocolVersion.V1.getVersion())
                .addHeader(ProtocolKey.LANGUAGE, Constants.LANGUAGE_JAVA)
                .setTimeout(timeout)
                .addBody(SendMessageRequestBody.PRODUCERGROUP, liteClientConfig.getProducerGroup())
                .addBody(SendMessageRequestBody.TOPIC, message.getTopic())
                .addBody(SendMessageRequestBody.CONTENT, message.getContent())
                .addBody(SendMessageRequestBody.TTL, String.valueOf(timeout))
                .addBody(SendMessageRequestBody.BIZSEQNO, message.getBizSeqNo())
                .addBody(SendMessageRequestBody.UNIQUEID, message.getUniqueId());

        long startTime = System.currentTimeMillis();
        String target = selectEventMesh();
        String res = "";

        try (CloseableHttpClient httpClient = setHttpClient()) {
            res = HttpUtil.post(httpClient, target, requestParam);
        }

        if (logger.isDebugEnabled()) {
            logger.debug("publish sync message by await, targetEventMesh:{}, cost:{}ms, message:{}, rtn:{}", target, System.currentTimeMillis() - startTime, message, res);
        }

        EventMeshRetObj ret = JsonUtils.deserialize(res, EventMeshRetObj.class);
        if (ret.getRetCode() == EventMeshRetCode.SUCCESS.getRetCode()) {
            LiteMessage eventMeshMessage = new LiteMessage();
            SendMessageResponseBody.ReplyMessage replyMessage =
                    JsonUtils.deserialize(ret.getRetMsg(), SendMessageResponseBody.ReplyMessage.class);
            eventMeshMessage.setContent(replyMessage.body).setProp(replyMessage.properties)
                    .setTopic(replyMessage.topic);
            return eventMeshMessage;
        }

        return null;
    }

    public void request(LiteMessage message, RRCallback rrCallback, long timeout) throws Exception {
        if (!started.get()) {
            start();
        }
        Preconditions.checkState(StringUtils.isNotBlank(message.getTopic()),
                "eventMeshMessage[topic] invalid");
        Preconditions.checkState(StringUtils.isNotBlank(message.getContent()),
                "eventMeshMessage[content] invalid");
        Preconditions.checkState(ObjectUtils.allNotNull(rrCallback),
                "rrCallback invalid");
        RequestParam requestParam = new RequestParam(HttpMethod.POST);
        requestParam.addHeader(ProtocolKey.REQUEST_CODE, String.valueOf(RequestCode.MSG_SEND_SYNC.getRequestCode()))
                .addHeader(ProtocolKey.ClientInstanceKey.ENV, liteClientConfig.getEnv())
                .addHeader(ProtocolKey.ClientInstanceKey.IDC, liteClientConfig.getIdc())
                .addHeader(ProtocolKey.ClientInstanceKey.IP, liteClientConfig.getIp())
                .addHeader(ProtocolKey.ClientInstanceKey.PID, liteClientConfig.getPid())
                .addHeader(ProtocolKey.ClientInstanceKey.SYS, liteClientConfig.getSys())
                .addHeader(ProtocolKey.ClientInstanceKey.USERNAME, liteClientConfig.getUserName())
                .addHeader(ProtocolKey.ClientInstanceKey.PASSWD, liteClientConfig.getPassword())
                .addHeader(ProtocolKey.VERSION, ProtocolVersion.V1.getVersion())
                .addHeader(ProtocolKey.LANGUAGE, Constants.LANGUAGE_JAVA)
                .setTimeout(timeout)
                .addBody(SendMessageRequestBody.PRODUCERGROUP, liteClientConfig.getProducerGroup())
                .addBody(SendMessageRequestBody.TOPIC, message.getTopic())
                .addBody(SendMessageRequestBody.CONTENT, message.getContent())
                .addBody(SendMessageRequestBody.TTL, String.valueOf(timeout))
                .addBody(SendMessageRequestBody.BIZSEQNO, message.getBizSeqNo())
                .addBody(SendMessageRequestBody.UNIQUEID, message.getUniqueId());

        long startTime = System.currentTimeMillis();
        String target = selectEventMesh();

        try (CloseableHttpClient httpClient = setHttpClient()) {
            HttpUtil.post(httpClient, null, target, requestParam, new RRCallbackResponseHandlerAdapter(message, rrCallback, timeout));
        }

        if (logger.isDebugEnabled()) {
            logger.debug("publish sync message by async, target:{}, cost:{}, message:{}", target, System.currentTimeMillis() - startTime, message);
        }
    }
}
