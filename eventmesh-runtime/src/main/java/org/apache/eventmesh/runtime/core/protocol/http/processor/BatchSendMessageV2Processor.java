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

import io.cloudevents.CloudEvent;
import io.cloudevents.core.v1.CloudEventBuilder;
import io.netty.channel.ChannelHandlerContext;
import org.apache.commons.lang3.StringUtils;
import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.api.SendResult;
import org.apache.eventmesh.api.exception.OnExceptionContext;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.IPUtil;
import org.apache.eventmesh.common.command.HttpCommand;
import org.apache.eventmesh.common.protocol.http.body.message.*;
import org.apache.eventmesh.common.protocol.http.common.EventMeshRetCode;
import org.apache.eventmesh.common.protocol.http.common.ProtocolKey;
import org.apache.eventmesh.common.protocol.http.common.RequestCode;
import org.apache.eventmesh.common.protocol.http.header.message.SendMessageBatchV2RequestHeader;
import org.apache.eventmesh.common.protocol.http.header.message.SendMessageBatchV2ResponseHeader;
import org.apache.eventmesh.protocol.api.ProtocolAdaptor;
import org.apache.eventmesh.protocol.api.ProtocolPluginFactory;
import org.apache.eventmesh.runtime.acl.Acl;
import org.apache.eventmesh.runtime.boot.EventMeshHTTPServer;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.protocol.http.async.AsyncContext;
import org.apache.eventmesh.runtime.core.protocol.http.processor.inf.HttpRequestProcessor;
import org.apache.eventmesh.runtime.core.protocol.http.producer.EventMeshProducer;
import org.apache.eventmesh.runtime.core.protocol.http.producer.SendMessageContext;
import org.apache.eventmesh.runtime.util.EventMeshUtil;
import org.apache.eventmesh.runtime.util.RemotingHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class BatchSendMessageV2Processor implements HttpRequestProcessor {

    public Logger cmdLogger = LoggerFactory.getLogger("cmd");

    public Logger aclLogger = LoggerFactory.getLogger("acl");

    private EventMeshHTTPServer eventMeshHTTPServer;

    public BatchSendMessageV2Processor(EventMeshHTTPServer eventMeshHTTPServer) {
        this.eventMeshHTTPServer = eventMeshHTTPServer;
    }

    public Logger batchMessageLogger = LoggerFactory.getLogger("batchMessage");

    @Override
    public void processRequest(ChannelHandlerContext ctx, AsyncContext<HttpCommand> asyncContext) throws Exception {

        HttpCommand responseEventMeshCommand;

        cmdLogger.info("cmd={}|{}|client2eventMesh|from={}|to={}", RequestCode.get(Integer.valueOf(asyncContext.getRequest().getRequestCode())),
                EventMeshConstants.PROTOCOL_HTTP,
                RemotingHelper.parseChannelRemoteAddr(ctx.channel()), IPUtil.getLocalAddress());

        ProtocolAdaptor httpCommandProtocolAdaptor = ProtocolPluginFactory.getProtocolAdaptor("cloudevents");
        CloudEvent event = httpCommandProtocolAdaptor.toCloudEventV1(asyncContext.getRequest());

        SendMessageBatchV2ResponseHeader sendMessageBatchV2ResponseHeader =
                SendMessageBatchV2ResponseHeader.buildHeader(Integer.valueOf(asyncContext.getRequest().getRequestCode()), eventMeshHTTPServer.getEventMeshHttpConfiguration().eventMeshCluster,
                        IPUtil.getLocalAddress(), eventMeshHTTPServer.getEventMeshHttpConfiguration().eventMeshEnv,
                        eventMeshHTTPServer.getEventMeshHttpConfiguration().eventMeshIDC);

        //validate event
        if (StringUtils.isBlank(event.getId())
                || event.getSource() != null
                || event.getSpecVersion() != null
                || StringUtils.isBlank(event.getType())
                || StringUtils.isBlank(event.getSubject())) {
            responseEventMeshCommand = asyncContext.getRequest().createHttpCommandResponse(
                    sendMessageBatchV2ResponseHeader,
                    SendMessageBatchV2ResponseBody.buildBody(EventMeshRetCode.EVENTMESH_PROTOCOL_HEADER_ERR.getRetCode(), EventMeshRetCode.EVENTMESH_PROTOCOL_HEADER_ERR.getErrMsg()));
            asyncContext.onComplete(responseEventMeshCommand);
            return;
        }

        String idc = Objects.requireNonNull(event.getExtension(ProtocolKey.ClientInstanceKey.IDC)).toString();
        String pid = Objects.requireNonNull(event.getExtension(ProtocolKey.ClientInstanceKey.PID)).toString();
        String sys = Objects.requireNonNull(event.getExtension(ProtocolKey.ClientInstanceKey.SYS)).toString();

        //validate event-extension
        if (StringUtils.isBlank(idc)
                || StringUtils.isBlank(pid)
                || !StringUtils.isNumeric(pid)
                || StringUtils.isBlank(sys)) {
            responseEventMeshCommand = asyncContext.getRequest().createHttpCommandResponse(
                    sendMessageBatchV2ResponseHeader,
                    SendMessageBatchV2ResponseBody.buildBody(EventMeshRetCode.EVENTMESH_PROTOCOL_HEADER_ERR.getRetCode(), EventMeshRetCode.EVENTMESH_PROTOCOL_HEADER_ERR.getErrMsg()));
            asyncContext.onComplete(responseEventMeshCommand);
            return;
        }

        String bizNo = Objects.requireNonNull(event.getExtension(SendMessageBatchV2RequestBody.BIZSEQNO)).toString();
        String producerGroup = Objects.requireNonNull(event.getExtension(SendMessageBatchV2RequestBody.PRODUCERGROUP)).toString();
        String topic = event.getSubject();

        if (StringUtils.isBlank(bizNo)
                || StringUtils.isBlank(topic)
                || StringUtils.isBlank(producerGroup)
                || event.getData() != null) {
            responseEventMeshCommand = asyncContext.getRequest().createHttpCommandResponse(
                    sendMessageBatchV2ResponseHeader,
                    SendMessageBatchV2ResponseBody.buildBody(EventMeshRetCode.EVENTMESH_PROTOCOL_BODY_ERR.getRetCode(), EventMeshRetCode.EVENTMESH_PROTOCOL_BODY_ERR.getErrMsg()));
            asyncContext.onComplete(responseEventMeshCommand);
            return;
        }

        //do acl check
        if (eventMeshHTTPServer.getEventMeshHttpConfiguration().eventMeshServerSecurityEnable) {
            String remoteAddr = RemotingHelper.parseChannelRemoteAddr(ctx.channel());
            String user = event.getExtension(ProtocolKey.ClientInstanceKey.USERNAME).toString();
            String pass = event.getExtension(ProtocolKey.ClientInstanceKey.PASSWD).toString();
            String subsystem = event.getExtension(ProtocolKey.ClientInstanceKey.SYS).toString();
            int requestCode = Integer.parseInt(asyncContext.getRequest().getRequestCode());
            try {
                Acl.doAclCheckInHttpSend(remoteAddr, user, pass, subsystem, topic, requestCode);
            } catch (Exception e) {
                //String errorMsg = String.format("CLIENT HAS NO PERMISSION,send failed, topic:%s, subsys:%s, realIp:%s", topic, subsys, realIp);

                responseEventMeshCommand = asyncContext.getRequest().createHttpCommandResponse(
                        sendMessageBatchV2ResponseHeader,
                        SendMessageResponseBody.buildBody(EventMeshRetCode.EVENTMESH_ACL_ERR.getRetCode(), e.getMessage()));
                asyncContext.onComplete(responseEventMeshCommand);
                aclLogger.warn("CLIENT HAS NO PERMISSION,BatchSendMessageV2Processor send failed", e);
                return;
            }
        }

        if (!eventMeshHTTPServer.getBatchRateLimiter()
                .tryAcquire(EventMeshConstants.DEFAULT_FASTFAIL_TIMEOUT_IN_MILLISECONDS, TimeUnit.MILLISECONDS)) {
            responseEventMeshCommand = asyncContext.getRequest().createHttpCommandResponse(
                    sendMessageBatchV2ResponseHeader,
                    SendMessageBatchV2ResponseBody.buildBody(EventMeshRetCode.EVENTMESH_BATCH_SPEED_OVER_LIMIT_ERR.getRetCode(), EventMeshRetCode.EVENTMESH_BATCH_SPEED_OVER_LIMIT_ERR.getErrMsg()));
            eventMeshHTTPServer.metrics.summaryMetrics
                    .recordSendBatchMsgDiscard(1);
            asyncContext.onComplete(responseEventMeshCommand);
            return;
        }

        EventMeshProducer batchEventMeshProducer = eventMeshHTTPServer.getProducerManager().getEventMeshProducer(producerGroup);
        batchEventMeshProducer.getMqProducerWrapper().getMeshMQProducer().setExtFields();
        if (!batchEventMeshProducer.getStarted().get()) {
            responseEventMeshCommand = asyncContext.getRequest().createHttpCommandResponse(
                    sendMessageBatchV2ResponseHeader,
                    SendMessageBatchV2ResponseBody.buildBody(EventMeshRetCode.EVENTMESH_BATCH_PRODUCER_STOPED_ERR.getRetCode(), EventMeshRetCode.EVENTMESH_BATCH_PRODUCER_STOPED_ERR.getErrMsg()));
            asyncContext.onComplete(responseEventMeshCommand);
            return;
        }

        long batchStartTime = System.currentTimeMillis();

        String ttl = String.valueOf(EventMeshConstants.DEFAULT_MSG_TTL_MILLS);
        if (StringUtils.isBlank(event.getExtension(SendMessageRequestBody.TTL).toString())
                && !StringUtils.isNumeric(event.getExtension(SendMessageRequestBody.TTL).toString())) {
            event = new CloudEventBuilder(event).withExtension(SendMessageRequestBody.TTL, ttl).build();
        }


        try {
            event = new CloudEventBuilder(event)
                    .withExtension("msgType", "persistent")
                    .withExtension(EventMeshConstants.REQ_C2EVENTMESH_TIMESTAMP, String.valueOf(System.currentTimeMillis()))
                    .withExtension(EventMeshConstants.REQ_EVENTMESH2MQ_TIMESTAMP, String.valueOf(System.currentTimeMillis()))
                    .build();
            if (batchMessageLogger.isDebugEnabled()) {
                batchMessageLogger.debug("msg2MQMsg suc, topic:{}, msg:{}", topic, event.getData());
            }

        } catch (Exception e) {
            batchMessageLogger.error("msg2MQMsg err, topic:{}, msg:{}", topic, event.getData(), e);
            responseEventMeshCommand = asyncContext.getRequest().createHttpCommandResponse(
                    sendMessageBatchV2ResponseHeader,
                    SendMessageBatchV2ResponseBody.buildBody(EventMeshRetCode.EVENTMESH_PACKAGE_MSG_ERR.getRetCode(), EventMeshRetCode.EVENTMESH_PACKAGE_MSG_ERR.getErrMsg() + EventMeshUtil.stackTrace(e, 2)));
            asyncContext.onComplete(responseEventMeshCommand);
            return;
        }

        eventMeshHTTPServer.metrics.summaryMetrics.recordSendBatchMsg(1);

        final SendMessageContext sendMessageContext = new SendMessageContext(bizNo, event, batchEventMeshProducer, eventMeshHTTPServer);

        try {
            batchEventMeshProducer.send(sendMessageContext, new SendCallback() {
                @Override
                public void onSuccess(SendResult sendResult) {
                    long batchEndTime = System.currentTimeMillis();
                    eventMeshHTTPServer.metrics.summaryMetrics.recordBatchSendMsgCost(batchEndTime - batchStartTime);
                    batchMessageLogger.debug("batchMessageV2|eventMesh2mq|REQ|ASYNC|bizSeqNo={}|send2MQCost={}ms|topic={}",
                            bizNo, batchEndTime - batchStartTime, topic);
                }

                @Override
                public void onException(OnExceptionContext context) {
                    long batchEndTime = System.currentTimeMillis();
                    eventMeshHTTPServer.getHttpRetryer().pushRetry(sendMessageContext.delay(10000));
                    eventMeshHTTPServer.metrics.summaryMetrics.recordBatchSendMsgCost(batchEndTime - batchStartTime);
                    batchMessageLogger.error("batchMessageV2|eventMesh2mq|REQ|ASYNC|bizSeqNo={}|send2MQCost={}ms|topic={}",
                            bizNo, batchEndTime - batchStartTime, topic, context.getException());
                }

            });
        } catch (Exception e) {
            responseEventMeshCommand = asyncContext.getRequest().createHttpCommandResponse(
                    sendMessageBatchV2ResponseHeader,
                    SendMessageBatchV2ResponseBody.buildBody(EventMeshRetCode.EVENTMESH_SEND_BATCHLOG_MSG_ERR.getRetCode(), EventMeshRetCode.EVENTMESH_SEND_BATCHLOG_MSG_ERR.getErrMsg() + EventMeshUtil.stackTrace(e, 2)));
            asyncContext.onComplete(responseEventMeshCommand);
            long batchEndTime = System.currentTimeMillis();
            eventMeshHTTPServer.getHttpRetryer().pushRetry(sendMessageContext.delay(10000));
            eventMeshHTTPServer.metrics.summaryMetrics.recordBatchSendMsgCost(batchEndTime - batchStartTime);
            batchMessageLogger.error("batchMessageV2|eventMesh2mq|REQ|ASYNC|bizSeqNo={}|send2MQCost={}ms|topic={}",
                    bizNo, batchEndTime - batchStartTime, topic, e);
        }

        responseEventMeshCommand = asyncContext.getRequest().createHttpCommandResponse(
                sendMessageBatchV2ResponseHeader,
                SendMessageBatchV2ResponseBody.buildBody(EventMeshRetCode.SUCCESS.getRetCode(), EventMeshRetCode.SUCCESS.getErrMsg()));
        asyncContext.onComplete(responseEventMeshCommand);

        return;
    }

    @Override
    public boolean rejectRequest() {
        return false;
    }
}

