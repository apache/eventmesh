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

import com.alibaba.fastjson.JSON;

import io.netty.channel.ChannelHandlerContext;
import io.openmessaging.api.Message;
import io.openmessaging.api.OnExceptionContext;
import io.openmessaging.api.SendCallback;
import io.openmessaging.api.SendResult;

import org.apache.commons.lang3.StringUtils;
import org.apache.eventmesh.api.RRCallback;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.IPUtil;
import org.apache.eventmesh.common.LiteMessage;
import org.apache.eventmesh.common.command.HttpCommand;
import org.apache.eventmesh.common.config.CommonConfiguration;
import org.apache.eventmesh.common.protocol.http.body.message.SendMessageRequestBody;
import org.apache.eventmesh.common.protocol.http.body.message.SendMessageResponseBody;
import org.apache.eventmesh.common.protocol.http.common.EventMeshRetCode;
import org.apache.eventmesh.common.protocol.http.common.RequestCode;
import org.apache.eventmesh.common.protocol.http.header.message.SendMessageRequestHeader;
import org.apache.eventmesh.common.protocol.http.header.message.SendMessageResponseHeader;
import org.apache.eventmesh.runtime.acl.Acl;
import org.apache.eventmesh.runtime.boot.EventMeshHTTPServer;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.protocol.http.async.AsyncContext;
import org.apache.eventmesh.runtime.core.protocol.http.async.CompleteHandler;
import org.apache.eventmesh.runtime.core.protocol.http.processor.inf.HttpRequestProcessor;
import org.apache.eventmesh.runtime.core.protocol.http.producer.EventMeshProducer;
import org.apache.eventmesh.runtime.core.protocol.http.producer.SendMessageContext;
import org.apache.eventmesh.runtime.util.EventMeshUtil;
import org.apache.eventmesh.runtime.util.OMSUtil;
import org.apache.eventmesh.runtime.util.RemotingHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SendSyncMessageProcessor implements HttpRequestProcessor {

    public Logger messageLogger = LoggerFactory.getLogger("message");

    public Logger cmdLogger = LoggerFactory.getLogger("cmd");

    public Logger httpLogger = LoggerFactory.getLogger("http");

    public Logger aclLogger = LoggerFactory.getLogger("acl");

    private EventMeshHTTPServer eventMeshHTTPServer;

    public SendSyncMessageProcessor(EventMeshHTTPServer eventMeshHTTPServer) {
        this.eventMeshHTTPServer = eventMeshHTTPServer;
    }

    @Override
    public void processRequest(ChannelHandlerContext ctx, AsyncContext<HttpCommand> asyncContext) throws Exception {

        HttpCommand responseEventMeshCommand;

        cmdLogger.info("cmd={}|{}|client2eventMesh|from={}|to={}", RequestCode.get(Integer.valueOf(asyncContext.getRequest().getRequestCode())),
                EventMeshConstants.PROTOCOL_HTTP,
                RemotingHelper.parseChannelRemoteAddr(ctx.channel()), IPUtil.getLocalAddress());

        SendMessageRequestHeader sendMessageRequestHeader = (SendMessageRequestHeader) asyncContext.getRequest().getHeader();
        SendMessageRequestBody sendMessageRequestBody = (SendMessageRequestBody) asyncContext.getRequest().getBody();

        SendMessageResponseHeader sendMessageResponseHeader =
                SendMessageResponseHeader.buildHeader(Integer.valueOf(asyncContext.getRequest().getRequestCode()), CommonConfiguration.eventMeshCluster,
                        IPUtil.getLocalAddress(), CommonConfiguration.eventMeshEnv, CommonConfiguration.eventMeshIDC);

        if (StringUtils.isBlank(sendMessageRequestHeader.getIdc())
                || StringUtils.isBlank(sendMessageRequestHeader.getPid())
                || !StringUtils.isNumeric(sendMessageRequestHeader.getPid())
                || StringUtils.isBlank(sendMessageRequestHeader.getSys())) {
            responseEventMeshCommand = asyncContext.getRequest().createHttpCommandResponse(
                    sendMessageResponseHeader,
                    SendMessageResponseBody.buildBody(EventMeshRetCode.EVENTMESH_PROTOCOL_HEADER_ERR.getRetCode(), EventMeshRetCode.EVENTMESH_PROTOCOL_HEADER_ERR.getErrMsg()));
            asyncContext.onComplete(responseEventMeshCommand);
            return;
        }

        if (StringUtils.isBlank(sendMessageRequestBody.getBizSeqNo())
                || StringUtils.isBlank(sendMessageRequestBody.getUniqueId())
                || StringUtils.isBlank(sendMessageRequestBody.getProducerGroup())
                || StringUtils.isBlank(sendMessageRequestBody.getTopic())
                || StringUtils.isBlank(sendMessageRequestBody.getContent())
                || (StringUtils.isBlank(sendMessageRequestBody.getTtl()))) {
            responseEventMeshCommand = asyncContext.getRequest().createHttpCommandResponse(
                    sendMessageResponseHeader,
                    SendMessageResponseBody.buildBody(EventMeshRetCode.EVENTMESH_PROTOCOL_BODY_ERR.getRetCode(), EventMeshRetCode.EVENTMESH_PROTOCOL_BODY_ERR.getErrMsg()));
            asyncContext.onComplete(responseEventMeshCommand);
            return;
        }

        //do acl check
        if(eventMeshHTTPServer.getEventMeshHttpConfiguration().eventMeshServerSecurityEnable) {
            String remoteAddr = RemotingHelper.parseChannelRemoteAddr(ctx.channel());
            String user = sendMessageRequestHeader.getUsername();
            String pass = sendMessageRequestHeader.getPasswd();
            String subsystem = sendMessageRequestHeader.getSys();
            int requestCode = Integer.valueOf(sendMessageRequestHeader.getCode());
            String topic = sendMessageRequestBody.getTopic();
            try {
                Acl.doAclCheckInHttpSend(remoteAddr, user, pass, subsystem, topic, requestCode);
            }catch (Exception e){
                //String errorMsg = String.format("CLIENT HAS NO PERMISSION,send failed, topic:%s, subsys:%s, realIp:%s", topic, subsys, realIp);

                responseEventMeshCommand = asyncContext.getRequest().createHttpCommandResponse(
                        sendMessageResponseHeader,
                        SendMessageResponseBody.buildBody(EventMeshRetCode.EVENTMESH_ACL_ERR.getRetCode(), e.getMessage()));
                asyncContext.onComplete(responseEventMeshCommand);
                aclLogger.warn("CLIENT HAS NO PERMISSION,SendSyncMessageProcessor send failed", e);
                return;
            }
        }

        String producerGroup = sendMessageRequestBody.getProducerGroup();
        EventMeshProducer eventMeshProducer = eventMeshHTTPServer.getProducerManager().getEventMeshProducer(producerGroup);

        if (!eventMeshProducer.getStarted().get()) {
            responseEventMeshCommand = asyncContext.getRequest().createHttpCommandResponse(
                    sendMessageResponseHeader,
                    SendMessageResponseBody.buildBody(EventMeshRetCode.EVENTMESH_GROUP_PRODUCER_STOPED_ERR.getRetCode(), EventMeshRetCode.EVENTMESH_GROUP_PRODUCER_STOPED_ERR.getErrMsg()));
            asyncContext.onComplete(responseEventMeshCommand);
            return;
        }

        String ttl = String.valueOf(EventMeshConstants.DEFAULT_MSG_TTL_MILLS);
        if (StringUtils.isNotBlank(sendMessageRequestBody.getTtl()) && StringUtils.isNumeric(sendMessageRequestBody.getTtl())) {
            ttl = sendMessageRequestBody.getTtl();
        }

        Message omsMsg = new Message();
        try {
            // body
            omsMsg.setBody(sendMessageRequestBody.getContent().getBytes(EventMeshConstants.DEFAULT_CHARSET));
            // topic
            omsMsg.setTopic(sendMessageRequestBody.getTopic());
            omsMsg.putSystemProperties(Constants.PROPERTY_MESSAGE_DESTINATION, sendMessageRequestBody.getTopic());
            if (!StringUtils.isBlank(sendMessageRequestBody.getTag())) {
                omsMsg.putUserProperties("Tag", sendMessageRequestBody.getTag());
            }
            // ttl
            omsMsg.putSystemProperties(Constants.PROPERTY_MESSAGE_TIMEOUT, ttl);
            // bizNo
            omsMsg.putSystemProperties(Constants.PROPERTY_MESSAGE_SEARCH_KEYS, sendMessageRequestBody.getBizSeqNo());
            omsMsg.putUserProperties("msgType", "persistent");
            omsMsg.putUserProperties(EventMeshConstants.REQ_C2EVENTMESH_TIMESTAMP, String.valueOf(System.currentTimeMillis()));
            omsMsg.putUserProperties(Constants.RMB_UNIQ_ID, sendMessageRequestBody.getUniqueId());
//            omsMsg.putUserProperties("REPLY_TO", eventMeshProducer.getMqProducerWrapper().getMeshMQProducer().buildMQClientId());

            if (messageLogger.isDebugEnabled()) {
                messageLogger.debug("msg2MQMsg suc, bizSeqNo={}, topic={}", sendMessageRequestBody.getBizSeqNo(),
                        sendMessageRequestBody.getTopic());
            }
            omsMsg.putUserProperties(EventMeshConstants.REQ_EVENTMESH2MQ_TIMESTAMP, String.valueOf(System.currentTimeMillis()));
        } catch (Exception e) {
            messageLogger.error("msg2MQMsg err, bizSeqNo={}, topic={}", sendMessageRequestBody.getBizSeqNo(),
                    sendMessageRequestBody.getTopic(), e);
            responseEventMeshCommand = asyncContext.getRequest().createHttpCommandResponse(
                    sendMessageResponseHeader,
                    SendMessageResponseBody.buildBody(EventMeshRetCode.EVENTMESH_PACKAGE_MSG_ERR.getRetCode(), EventMeshRetCode.EVENTMESH_PACKAGE_MSG_ERR.getErrMsg() + EventMeshUtil.stackTrace(e, 2)));
            asyncContext.onComplete(responseEventMeshCommand);
            return;
        }

        final SendMessageContext sendMessageContext = new SendMessageContext(sendMessageRequestBody.getBizSeqNo(), omsMsg, eventMeshProducer, eventMeshHTTPServer);
        eventMeshHTTPServer.metrics.summaryMetrics.recordSendMsg();

        long startTime = System.currentTimeMillis();

        final CompleteHandler<HttpCommand> handler = new CompleteHandler<HttpCommand>() {
            @Override
            public void onResponse(HttpCommand httpCommand) {
                try {
                    if (httpLogger.isDebugEnabled()) {
                        httpLogger.debug("{}", httpCommand);
                    }
                    eventMeshHTTPServer.sendResponse(ctx, httpCommand.httpResponse());
                    eventMeshHTTPServer.metrics.summaryMetrics.recordHTTPReqResTimeCost(System.currentTimeMillis() - asyncContext.getRequest().getReqTime());
                } catch (Exception ex) {
                }
            }
        };

        LiteMessage liteMessage = new LiteMessage(sendMessageRequestBody.getBizSeqNo(),
                sendMessageRequestBody.getUniqueId(), sendMessageRequestBody.getTopic(),
                sendMessageRequestBody.getContent())
                .setProp(sendMessageRequestBody.getExtFields());

        try {
            eventMeshProducer.request(sendMessageContext, new RRCallback() {
                @Override
                public void onSuccess(Message omsMsg) {
                    omsMsg.getUserProperties().put(Constants.PROPERTY_MESSAGE_BORN_TIMESTAMP, omsMsg.getSystemProperties("BORN_TIMESTAMP"));
                    omsMsg.getUserProperties().put(EventMeshConstants.STORE_TIMESTAMP, omsMsg.getSystemProperties("STORE_TIMESTAMP"));
                    omsMsg.getUserProperties().put(EventMeshConstants.RSP_MQ2EVENTMESH_TIMESTAMP, String.valueOf(System.currentTimeMillis()));
                    messageLogger.info("message|mq2eventMesh|RSP|SYNC|rrCost={}ms|topic={}|bizSeqNo={}|uniqueId={}",
                            System.currentTimeMillis() - startTime,
                            sendMessageRequestBody.getTopic(),
                            sendMessageRequestBody.getBizSeqNo(),
                            sendMessageRequestBody.getUniqueId());

                    try {
                        final String rtnMsg = new String(omsMsg.getBody(), EventMeshConstants.DEFAULT_CHARSET);
                        omsMsg.getUserProperties().put(EventMeshConstants.RSP_EVENTMESH2C_TIMESTAMP, String.valueOf(System.currentTimeMillis()));
                        HttpCommand succ = asyncContext.getRequest().createHttpCommandResponse(
                                sendMessageResponseHeader,
                                SendMessageResponseBody.buildBody(EventMeshRetCode.SUCCESS.getRetCode(),
                                        JSON.toJSONString(new SendMessageResponseBody.ReplyMessage(omsMsg.getSystemProperties(Constants.PROPERTY_MESSAGE_DESTINATION), rtnMsg,
                                                OMSUtil.combineProp(omsMsg.getSystemProperties(),
                                                        omsMsg.getUserProperties()))
                                        )));
                        asyncContext.onComplete(succ, handler);
                    } catch (Exception ex) {
                        HttpCommand err = asyncContext.getRequest().createHttpCommandResponse(
                                sendMessageResponseHeader,
                                SendMessageResponseBody.buildBody(EventMeshRetCode.EVENTMESH_WAITING_RR_MSG_ERR.getRetCode(),
                                        EventMeshRetCode.EVENTMESH_WAITING_RR_MSG_ERR.getErrMsg() + EventMeshUtil.stackTrace(ex, 2)));
                        asyncContext.onComplete(err, handler);
                        messageLogger.warn("message|mq2eventMesh|RSP", ex);
                    }
                }

                @Override
                public void onException(Throwable e) {
                    HttpCommand err = asyncContext.getRequest().createHttpCommandResponse(
                            sendMessageResponseHeader,
                            SendMessageResponseBody.buildBody(EventMeshRetCode.EVENTMESH_WAITING_RR_MSG_ERR.getRetCode(),
                                    EventMeshRetCode.EVENTMESH_WAITING_RR_MSG_ERR.getErrMsg() + EventMeshUtil.stackTrace(e, 2)));
                    asyncContext.onComplete(err, handler);
                    messageLogger.error("message|mq2eventMesh|RSP|SYNC|rrCost={}ms|topic={}|bizSeqNo={}|uniqueId={}",
                            System.currentTimeMillis() - startTime,
                            sendMessageRequestBody.getTopic(),
                            sendMessageRequestBody.getBizSeqNo(),
                            sendMessageRequestBody.getUniqueId(), e);
                }
            }, Integer.valueOf(ttl));
        } catch (Exception ex) {
            HttpCommand err = asyncContext.getRequest().createHttpCommandResponse(
                    sendMessageResponseHeader,
                    SendMessageResponseBody.buildBody(EventMeshRetCode.EVENTMESH_SEND_SYNC_MSG_ERR.getRetCode(),
                            EventMeshRetCode.EVENTMESH_SEND_SYNC_MSG_ERR.getErrMsg() + EventMeshUtil.stackTrace(ex, 2)));
            asyncContext.onComplete(err);
            long endTime = System.currentTimeMillis();
            eventMeshHTTPServer.metrics.summaryMetrics.recordSendMsgFailed();
            eventMeshHTTPServer.metrics.summaryMetrics.recordSendMsgCost(endTime - startTime);
            messageLogger.error("message|eventMesh2mq|REQ|SYNC|send2MQCost={}ms|topic={}|bizSeqNo={}|uniqueId={}",
                    endTime - startTime,
                    sendMessageRequestBody.getTopic(),
                    sendMessageRequestBody.getBizSeqNo(),
                    sendMessageRequestBody.getUniqueId(), ex);
        }

        return;
    }

    @Override
    public boolean rejectRequest() {
        return false;
    }
}
