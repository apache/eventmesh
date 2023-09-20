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

package org.apache.eventmesh.runtime.core.protocol.http.processor;

import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.api.SendResult;
import org.apache.eventmesh.api.exception.OnExceptionContext;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.protocol.ProtocolTransportObject;
import org.apache.eventmesh.common.protocol.http.HttpCommand;
import org.apache.eventmesh.common.protocol.http.body.message.SendMessageBatchV2RequestBody;
import org.apache.eventmesh.common.protocol.http.body.message.SendMessageBatchV2ResponseBody;
import org.apache.eventmesh.common.protocol.http.body.message.SendMessageRequestBody;
import org.apache.eventmesh.common.protocol.http.common.EventMeshRetCode;
import org.apache.eventmesh.common.protocol.http.common.ProtocolKey;
import org.apache.eventmesh.common.protocol.http.common.RequestCode;
import org.apache.eventmesh.common.protocol.http.header.message.SendMessageBatchV2RequestHeader;
import org.apache.eventmesh.common.protocol.http.header.message.SendMessageBatchV2ResponseHeader;
import org.apache.eventmesh.metrics.api.model.HttpSummaryMetrics;
import org.apache.eventmesh.protocol.api.ProtocolAdaptor;
import org.apache.eventmesh.protocol.api.ProtocolPluginFactory;
import org.apache.eventmesh.runtime.acl.Acl;
import org.apache.eventmesh.runtime.boot.EventMeshHTTPServer;
import org.apache.eventmesh.runtime.configuration.EventMeshHTTPConfiguration;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.protocol.http.async.AsyncContext;
import org.apache.eventmesh.runtime.core.protocol.http.processor.inf.HttpRequestProcessor;
import org.apache.eventmesh.runtime.core.protocol.http.producer.EventMeshProducer;
import org.apache.eventmesh.runtime.core.protocol.http.producer.SendMessageContext;
import org.apache.eventmesh.runtime.util.EventMeshUtil;
import org.apache.eventmesh.runtime.util.RemotingHelper;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.netty.channel.ChannelHandlerContext;

public class BatchSendMessageV2Processor implements HttpRequestProcessor {

    private final Logger cmdLogger = LoggerFactory.getLogger(EventMeshConstants.CMD);

    private final Logger aclLogger = LoggerFactory.getLogger(EventMeshConstants.ACL);

    private final Logger batchMessageLogger = LoggerFactory.getLogger("batchMessage");

    private final EventMeshHTTPServer eventMeshHTTPServer;

    private final Acl acl;

    public BatchSendMessageV2Processor(EventMeshHTTPServer eventMeshHTTPServer) {
        this.eventMeshHTTPServer = eventMeshHTTPServer;
        this.acl = eventMeshHTTPServer.getAcl();
    }

    @Override
    public void processRequest(ChannelHandlerContext ctx, AsyncContext<HttpCommand> asyncContext) throws Exception {
        final HttpCommand request = asyncContext.getRequest();
        final Integer requestCode = Integer.valueOf(request.getRequestCode());

        cmdLogger.info("cmd={}|{}|client2eventMesh|from={}|to={}",
            RequestCode.get(requestCode),
            EventMeshConstants.PROTOCOL_HTTP,
            RemotingHelper.parseChannelRemoteAddr(ctx.channel()),
            RemotingHelper.parseChannelRemoteAddr(ctx.channel()));

        SendMessageBatchV2RequestHeader sendMessageBatchV2RequestHeader =
            (SendMessageBatchV2RequestHeader) request.getHeader();

        String protocolType = sendMessageBatchV2RequestHeader.getProtocolType();
        ProtocolAdaptor<ProtocolTransportObject> httpCommandProtocolAdaptor =
            ProtocolPluginFactory.getProtocolAdaptor(protocolType);
        CloudEvent event = httpCommandProtocolAdaptor.toCloudEvent(request);

        EventMeshHTTPConfiguration httpConfiguration = eventMeshHTTPServer.getEventMeshHttpConfiguration();
        SendMessageBatchV2ResponseHeader sendMessageBatchV2ResponseHeader =
            SendMessageBatchV2ResponseHeader.buildHeader(
                requestCode,
                httpConfiguration.getEventMeshCluster(),
                httpConfiguration.getEventMeshEnv(),
                httpConfiguration.getEventMeshIDC());

        // todo: use validate processor to check
        // validate event
        if (!ObjectUtils.allNotNull(event.getSource(), event.getSpecVersion())
            || StringUtils.isAnyBlank(event.getId(), event.getType(), event.getSubject())) {
            completeResponse(request, asyncContext, sendMessageBatchV2ResponseHeader,
                EventMeshRetCode.EVENTMESH_PROTOCOL_HEADER_ERR, null, SendMessageBatchV2ResponseBody.class);
            return;
        }

        String idc = getExtension(event, ProtocolKey.ClientInstanceKey.IDC.getKey());
        String pid = getExtension(event, ProtocolKey.ClientInstanceKey.PID.getKey());
        String sys = getExtension(event, ProtocolKey.ClientInstanceKey.SYS.getKey());

        // validate event-extension
        if (StringUtils.isAnyBlank(idc, pid, sys)
            || !StringUtils.isNumeric(pid)) {
            completeResponse(request, asyncContext, sendMessageBatchV2ResponseHeader,
                EventMeshRetCode.EVENTMESH_PROTOCOL_HEADER_ERR, null, SendMessageBatchV2ResponseBody.class);
            return;
        }

        String bizNo = getExtension(event, SendMessageBatchV2RequestBody.BIZSEQNO);
        String producerGroup = getExtension(event, SendMessageBatchV2RequestBody.PRODUCERGROUP);
        String topic = event.getSubject();

        if (StringUtils.isAnyBlank(bizNo, topic, producerGroup)
            || event.getData() == null) {
            completeResponse(request, asyncContext, sendMessageBatchV2ResponseHeader,
                EventMeshRetCode.EVENTMESH_PROTOCOL_BODY_ERR, null, SendMessageBatchV2ResponseBody.class);
            return;
        }

        String content = new String(Objects.requireNonNull(event.getData()).toBytes(), Constants.DEFAULT_CHARSET);
        if (content.length() > httpConfiguration.getEventMeshEventSize()) {
            batchMessageLogger.error("Event size exceeds the limit: {}", httpConfiguration.getEventMeshEventSize());
            completeResponse(request, asyncContext, sendMessageBatchV2ResponseHeader,
                EventMeshRetCode.EVENTMESH_PROTOCOL_BODY_ERR,
                "Event size exceeds the limit: " + httpConfiguration.getEventMeshEventSize(),
                SendMessageBatchV2ResponseBody.class);
            return;
        }

        // do acl check
        if (httpConfiguration.isEventMeshServerSecurityEnable()) {
            String remoteAddr = RemotingHelper.parseChannelRemoteAddr(ctx.channel());
            String user = getExtension(event, ProtocolKey.ClientInstanceKey.USERNAME.getKey());
            String pass = getExtension(event, ProtocolKey.ClientInstanceKey.PASSWD.getKey());
            String subsystem = getExtension(event, ProtocolKey.ClientInstanceKey.SYS.getKey());
            try {
                this.acl.doAclCheckInHttpSend(remoteAddr, user, pass, subsystem, topic, requestCode);
            } catch (Exception e) {
                completeResponse(request, asyncContext, sendMessageBatchV2ResponseHeader,
                    EventMeshRetCode.EVENTMESH_ACL_ERR, e.getMessage(), SendMessageBatchV2ResponseBody.class);
                aclLogger.warn("CLIENT HAS NO PERMISSION,BatchSendMessageV2Processor send failed", e);
                return;
            }
        }

        HttpSummaryMetrics summaryMetrics = eventMeshHTTPServer.getMetrics().getSummaryMetrics();
        if (!eventMeshHTTPServer.getBatchRateLimiter()
            .tryAcquire(EventMeshConstants.DEFAULT_FASTFAIL_TIMEOUT_IN_MILLISECONDS, TimeUnit.MILLISECONDS)) {
            summaryMetrics.recordSendBatchMsgDiscard(1);
            completeResponse(request, asyncContext, sendMessageBatchV2ResponseHeader,
                EventMeshRetCode.EVENTMESH_BATCH_SPEED_OVER_LIMIT_ERR, null, SendMessageBatchV2ResponseBody.class);
            return;
        }

        EventMeshProducer batchEventMeshProducer =
            eventMeshHTTPServer.getProducerManager().getEventMeshProducer(producerGroup);
        batchEventMeshProducer.getMqProducerWrapper().getMeshMQProducer().setExtFields();
        if (!batchEventMeshProducer.isStarted()) {
            completeResponse(request, asyncContext, sendMessageBatchV2ResponseHeader,
                EventMeshRetCode.EVENTMESH_BATCH_PRODUCER_STOPED_ERR, null, SendMessageBatchV2ResponseBody.class);
            return;
        }

        long batchStartTime = System.currentTimeMillis();

        String defaultTTL = String.valueOf(EventMeshConstants.DEFAULT_MSG_TTL_MILLS);
        // todo: use hashmap to avoid copy
        String ttlValue = getExtension(event, SendMessageRequestBody.TTL);
        if (StringUtils.isBlank(ttlValue) && !StringUtils.isNumeric(ttlValue)) {
            event = CloudEventBuilder.from(event).withExtension(SendMessageRequestBody.TTL, defaultTTL)
                .build();
        }

        try {
            event = CloudEventBuilder.from(event)
                .withExtension("msgtype", "persistent")
                .withExtension(EventMeshConstants.REQ_C2EVENTMESH_TIMESTAMP,
                    String.valueOf(System.currentTimeMillis()))
                .withExtension(EventMeshConstants.REQ_EVENTMESH2MQ_TIMESTAMP,
                    String.valueOf(System.currentTimeMillis()))
                .build();
            if (batchMessageLogger.isDebugEnabled()) {
                batchMessageLogger.debug("msg2MQMsg suc, topic:{}, msg:{}", topic, event.getData());
            }

        } catch (Exception e) {
            batchMessageLogger.error("msg2MQMsg err, topic:{}, msg:{}", topic, event.getData(), e);
            completeResponse(request, asyncContext, sendMessageBatchV2ResponseHeader, EventMeshRetCode.EVENTMESH_PACKAGE_MSG_ERR,
                EventMeshRetCode.EVENTMESH_PACKAGE_MSG_ERR.getErrMsg()
                    +
                    EventMeshUtil.stackTrace(e, 2),
                SendMessageBatchV2ResponseBody.class);
            return;
        }

        summaryMetrics.recordSendBatchMsg(1);

        final SendMessageContext sendMessageContext =
            new SendMessageContext(bizNo, event, batchEventMeshProducer, eventMeshHTTPServer);

        try {
            batchEventMeshProducer.send(sendMessageContext, new SendCallback() {

                @Override
                public void onSuccess(SendResult sendResult) {
                    long batchEndTime = System.currentTimeMillis();
                    summaryMetrics.recordBatchSendMsgCost(batchEndTime - batchStartTime);
                    batchMessageLogger.debug(
                        "batchMessageV2|eventMesh2mq|REQ|ASYNC|bizSeqNo={}|send2MQCost={}ms|topic={}",
                        bizNo, batchEndTime - batchStartTime, topic);
                }

                @Override
                public void onException(OnExceptionContext context) {
                    long batchEndTime = System.currentTimeMillis();
                    eventMeshHTTPServer.getHttpRetryer().pushRetry(sendMessageContext.delay(10000));
                    summaryMetrics.recordBatchSendMsgCost(batchEndTime - batchStartTime);
                    batchMessageLogger.error(
                        "batchMessageV2|eventMesh2mq|REQ|ASYNC|bizSeqNo={}|send2MQCost={}ms|topic={}",
                        bizNo, batchEndTime - batchStartTime, topic, context.getException());
                }

            });
        } catch (Exception e) {
            completeResponse(request, asyncContext, sendMessageBatchV2ResponseHeader, EventMeshRetCode.EVENTMESH_SEND_BATCHLOG_MSG_ERR,
                EventMeshRetCode.EVENTMESH_SEND_BATCHLOG_MSG_ERR.getErrMsg()
                    +
                    EventMeshUtil.stackTrace(e, 2),
                SendMessageBatchV2ResponseBody.class);
            long batchEndTime = System.currentTimeMillis();
            eventMeshHTTPServer.getHttpRetryer().pushRetry(sendMessageContext.delay(10000));
            summaryMetrics.recordBatchSendMsgCost(batchEndTime - batchStartTime);
            batchMessageLogger.error(
                "batchMessageV2|eventMesh2mq|REQ|ASYNC|bizSeqNo={}|send2MQCost={}ms|topic={}",
                bizNo, batchEndTime - batchStartTime, topic, e);
        }

        completeResponse(request, asyncContext, sendMessageBatchV2ResponseHeader,
            EventMeshRetCode.SUCCESS, null, SendMessageBatchV2ResponseBody.class);
    }
}
