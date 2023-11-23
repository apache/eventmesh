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
import org.apache.eventmesh.api.exception.AclException;
import org.apache.eventmesh.api.exception.OnExceptionContext;
import org.apache.eventmesh.api.meta.bo.EventMeshServicePubTopicInfo;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.protocol.ProtocolTransportObject;
import org.apache.eventmesh.common.protocol.http.HttpEventWrapper;
import org.apache.eventmesh.common.protocol.http.common.EventMeshRetCode;
import org.apache.eventmesh.common.protocol.http.common.ProtocolKey;
import org.apache.eventmesh.common.protocol.http.common.RequestURI;
import org.apache.eventmesh.common.utils.IPUtils;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.common.utils.LogUtils;
import org.apache.eventmesh.common.utils.RandomStringUtils;
import org.apache.eventmesh.filter.pattern.Pattern;
import org.apache.eventmesh.protocol.api.ProtocolAdaptor;
import org.apache.eventmesh.protocol.api.ProtocolPluginFactory;
import org.apache.eventmesh.runtime.acl.Acl;
import org.apache.eventmesh.runtime.boot.EventMeshHTTPServer;
import org.apache.eventmesh.runtime.common.EventMeshTrace;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.protocol.http.async.AsyncContext;
import org.apache.eventmesh.runtime.core.protocol.http.producer.EventMeshProducer;
import org.apache.eventmesh.runtime.core.protocol.http.producer.SendMessageContext;
import org.apache.eventmesh.runtime.util.EventMeshUtil;
import org.apache.eventmesh.runtime.util.RemotingHelper;
import org.apache.eventmesh.trace.api.common.EventMeshTraceConstants;

import org.apache.commons.lang3.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import io.cloudevents.CloudEvent;
import io.cloudevents.SpecVersion;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpRequest;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@EventMeshTrace(isEnable = true)
public class SendAsyncEventProcessor implements AsyncHttpProcessor {

    private final transient EventMeshHTTPServer eventMeshHTTPServer;

    private final Acl acl;

    public SendAsyncEventProcessor(EventMeshHTTPServer eventMeshHTTPServer) {
        this.eventMeshHTTPServer = eventMeshHTTPServer;
        this.acl = eventMeshHTTPServer.getAcl();
    }

    @Override
    public void handler(final HandlerService.HandlerSpecific handlerSpecific, final HttpRequest httpRequest) throws Exception {

        final AsyncContext<HttpEventWrapper> asyncContext = handlerSpecific.getAsyncContext();
        final ChannelHandlerContext ctx = handlerSpecific.getCtx();
        final HttpEventWrapper requestWrapper = asyncContext.getRequest();

        final String localAddress = IPUtils.getLocalAddress();

        LogUtils.info(log, "uri={}|{}|client2eventMesh|from={}|to={}", requestWrapper.getRequestURI(),
            EventMeshConstants.PROTOCOL_HTTP, RemotingHelper.parseChannelRemoteAddr(ctx.channel()), localAddress);

        // user request header
        final Map<String, Object> requestHeaderMap = requestWrapper.getHeaderMap();
        final String source = RemotingHelper.parseChannelRemoteAddr(ctx.channel());
        requestHeaderMap.put(ProtocolKey.ClientInstanceKey.IP.getKey(), source);

        // build sys header
        requestWrapper.buildSysHeaderForClient();

        // build cloudevents attributes
        requestHeaderMap.putIfAbsent("source", source);
        requestWrapper.buildSysHeaderForCE();

        final String bizNo = requestHeaderMap.getOrDefault(ProtocolKey.ClientInstanceKey.BIZSEQNO.getKey(),
            RandomStringUtils.generateNum(32)).toString();
        final String uniqueId = requestHeaderMap.getOrDefault(ProtocolKey.ClientInstanceKey.UNIQUEID.getKey(),
            RandomStringUtils.generateNum(32)).toString();
        final String ttl = requestHeaderMap.getOrDefault(Constants.EVENTMESH_MESSAGE_CONST_TTL,
            14400000).toString();

        requestWrapper.getSysHeaderMap().putIfAbsent(ProtocolKey.ClientInstanceKey.BIZSEQNO.getKey(), bizNo);
        requestWrapper.getSysHeaderMap().putIfAbsent(ProtocolKey.ClientInstanceKey.UNIQUEID.getKey(), uniqueId);
        requestWrapper.getSysHeaderMap().putIfAbsent(Constants.EVENTMESH_MESSAGE_CONST_TTL, ttl);

        final Map<String, Object> responseHeaderMap = new HashMap<>();
        responseHeaderMap.put(ProtocolKey.REQUEST_URI, requestWrapper.getRequestURI());
        responseHeaderMap.put(ProtocolKey.EventMeshInstanceKey.EVENTMESHCLUSTER,
            eventMeshHTTPServer.getEventMeshHttpConfiguration().getEventMeshCluster());
        responseHeaderMap.put(ProtocolKey.EventMeshInstanceKey.EVENTMESHIP,
            localAddress);
        responseHeaderMap.put(ProtocolKey.EventMeshInstanceKey.EVENTMESHENV,
            eventMeshHTTPServer.getEventMeshHttpConfiguration().getEventMeshEnv());
        responseHeaderMap.put(ProtocolKey.EventMeshInstanceKey.EVENTMESHIDC,
            eventMeshHTTPServer.getEventMeshHttpConfiguration().getEventMeshIDC());

        final Map<String, Object> responseBodyMap = new HashMap<>();

        final String protocolType = requestHeaderMap.getOrDefault(ProtocolKey.PROTOCOL_TYPE,
            "http").toString();

        final ProtocolAdaptor<ProtocolTransportObject> httpProtocolAdaptor =
            ProtocolPluginFactory.getProtocolAdaptor(protocolType);

        CloudEvent event = httpProtocolAdaptor.toCloudEvent(requestWrapper);

        // validate event
        if (event == null
            || event.getSource() == null
            || event.getSpecVersion() == null
            || StringUtils.isAnyBlank(event.getId(), event.getType(), event.getSubject())) {

            handlerSpecific.sendErrorResponse(EventMeshRetCode.EVENTMESH_PROTOCOL_HEADER_ERR, responseHeaderMap,
                responseBodyMap, EventMeshUtil.getCloudEventExtensionMap(SpecVersion.V1.toString(), event));

            return;
        }

        final String idc = Objects.requireNonNull(event.getExtension(ProtocolKey.ClientInstanceKey.IDC.getKey())).toString();
        final String pid = Objects.requireNonNull(event.getExtension(ProtocolKey.ClientInstanceKey.PID.getKey())).toString();
        final String sys = Objects.requireNonNull(event.getExtension(ProtocolKey.ClientInstanceKey.SYS.getKey())).toString();

        // validate event-extension

        if (StringUtils.isAnyBlank(idc, pid, sys)
            || !StringUtils.isNumeric(pid)) {
            handlerSpecific.sendErrorResponse(EventMeshRetCode.EVENTMESH_PROTOCOL_HEADER_ERR, responseHeaderMap,
                responseBodyMap, EventMeshUtil.getCloudEventExtensionMap(SpecVersion.V1.toString(), event));
            return;
        }

        final String producerGroup = Objects.requireNonNull(
            event.getExtension(ProtocolKey.ClientInstanceKey.PRODUCERGROUP.getKey())).toString();
        final String topic = event.getSubject();

        Pattern filterPattern = eventMeshHTTPServer.getFilterEngine().getFilterPattern(producerGroup + "-" + topic);

        // validate body
        if (StringUtils.isAnyBlank(bizNo, uniqueId, producerGroup, topic)
            || event.getData() == null) {
            handlerSpecific.sendErrorResponse(EventMeshRetCode.EVENTMESH_PROTOCOL_BODY_ERR, responseHeaderMap,
                responseBodyMap, EventMeshUtil.getCloudEventExtensionMap(SpecVersion.V1.toString(), event));
            return;
        }

        final String token = Objects.requireNonNull(event.getExtension(ProtocolKey.ClientInstanceKey.TOKEN.getKey())).toString();
        // do acl check
        if (eventMeshHTTPServer.getEventMeshHttpConfiguration().isEventMeshServerSecurityEnable()) {
            final String remoteAddr = RemotingHelper.parseChannelRemoteAddr(ctx.channel());
            final String requestURI = requestWrapper.getRequestURI();
            String subsystem = Objects.requireNonNull(event.getExtension(ProtocolKey.ClientInstanceKey.SYS.getKey())).toString();
            try {
                EventMeshServicePubTopicInfo eventMeshServicePubTopicInfo = eventMeshHTTPServer.getEventMeshServer()
                    .getProducerTopicManager().getEventMeshServicePubTopicInfo(producerGroup);
                if (eventMeshServicePubTopicInfo == null) {
                    throw new AclException("no group register");
                }
                this.acl.doAclCheckInHttpSend(remoteAddr, token, subsystem, topic, requestURI, eventMeshServicePubTopicInfo);
            } catch (Exception e) {
                handlerSpecific.sendErrorResponse(EventMeshRetCode.EVENTMESH_ACL_ERR, responseHeaderMap,
                    responseBodyMap, EventMeshUtil.getCloudEventExtensionMap(SpecVersion.V1.toString(), event));
                LogUtils.warn(log, "CLIENT HAS NO PERMISSION,SendAsyncMessageProcessor send failed", e);
                return;
            }
        }

        // control flow rate limit
        if (!eventMeshHTTPServer.getMsgRateLimiter()
            .tryAcquire(EventMeshConstants.DEFAULT_FASTFAIL_TIMEOUT_IN_MILLISECONDS, TimeUnit.MILLISECONDS)) {
            handlerSpecific.sendErrorResponse(EventMeshRetCode.EVENTMESH_HTTP_MES_SEND_OVER_LIMIT_ERR, responseHeaderMap,
                responseBodyMap, EventMeshUtil.getCloudEventExtensionMap(SpecVersion.V1.toString(), event));
            return;
        }

        final EventMeshProducer eventMeshProducer;
        if (StringUtils.isNotBlank(token)) {
            eventMeshProducer = eventMeshHTTPServer.getProducerManager().getEventMeshProducer(producerGroup, token);
        } else {
            eventMeshProducer = eventMeshHTTPServer.getProducerManager().getEventMeshProducer(producerGroup);
        }

        if (!eventMeshProducer.isStarted()) {
            handlerSpecific.sendErrorResponse(EventMeshRetCode.EVENTMESH_GROUP_PRODUCER_STOPED_ERR, responseHeaderMap,
                responseBodyMap, EventMeshUtil.getCloudEventExtensionMap(SpecVersion.V1.toString(), event));
            return;
        }

        final String content = new String(Objects.requireNonNull(event.getData()).toBytes(), StandardCharsets.UTF_8);
        if (Objects.requireNonNull(content).length() > eventMeshHTTPServer.getEventMeshHttpConfiguration().getEventMeshEventSize()) {
            LogUtils.error(log, "Event size exceeds the limit: {}",
                eventMeshHTTPServer.getEventMeshHttpConfiguration().getEventMeshEventSize());
            handlerSpecific.sendErrorResponse(EventMeshRetCode.EVENTMESH_PROTOCOL_BODY_SIZE_ERR, responseHeaderMap,
                responseBodyMap, EventMeshUtil.getCloudEventExtensionMap(SpecVersion.V1.toString(), event));
            return;
        }

        try {
            event = CloudEventBuilder.from(event)
                .withExtension(EventMeshConstants.MSG_TYPE, EventMeshConstants.PERSISTENT)
                .withExtension(EventMeshConstants.REQ_C2EVENTMESH_TIMESTAMP, String.valueOf(System.currentTimeMillis()))
                .withExtension(EventMeshConstants.REQ_EVENTMESH2MQ_TIMESTAMP, String.valueOf(System.currentTimeMillis()))
                .build();

            LogUtils.debug(log, "msg2MQMsg suc, bizSeqNo={}, topic={}", bizNo, topic);
        } catch (Exception e) {
            LogUtils.error(log, "msg2MQMsg err, bizSeqNo={}, topic={}", bizNo, topic, e);
            handlerSpecific.sendErrorResponse(EventMeshRetCode.EVENTMESH_PACKAGE_MSG_ERR, responseHeaderMap,
                responseBodyMap, EventMeshUtil.getCloudEventExtensionMap(SpecVersion.V1.toString(), event));
            return;
        }

        final SendMessageContext sendMessageContext = new SendMessageContext(bizNo, event, eventMeshProducer,
            eventMeshHTTPServer);
        eventMeshHTTPServer.getMetrics().getSummaryMetrics().recordSendMsg();

        final long startTime = System.currentTimeMillis();
        boolean isFiltered = true;
        try {
            event = CloudEventBuilder.from(sendMessageContext.getEvent())
                .withExtension(EventMeshConstants.REQ_EVENTMESH2MQ_TIMESTAMP, String.valueOf(System.currentTimeMillis()))
                .build();
            handlerSpecific.getTraceOperation().createClientTraceOperation(EventMeshUtil.getCloudEventExtensionMap(SpecVersion.V1.toString(), event),
                EventMeshTraceConstants.TRACE_UPSTREAM_EVENTMESH_CLIENT_SPAN, false);
            if (filterPattern != null) {
                isFiltered = filterPattern.filter(JsonUtils.toJSONString(event));
            }

            if (isFiltered) {
                eventMeshProducer.send(sendMessageContext, new SendCallback() {

                    @Override
                    public void onSuccess(final SendResult sendResult) {
                        responseBodyMap.put(EventMeshConstants.RET_CODE, EventMeshRetCode.SUCCESS.getRetCode());
                        responseBodyMap.put(EventMeshConstants.RET_MSG, EventMeshRetCode.SUCCESS.getErrMsg() + sendResult);

                        LogUtils.info(log, "message|eventMesh2mq|REQ|ASYNC|send2MQCost={}ms|topic={}|bizSeqNo={}|uniqueId={}",
                            System.currentTimeMillis() - startTime, topic, bizNo, uniqueId);
                        handlerSpecific.getTraceOperation().endLatestTrace(sendMessageContext.getEvent());
                        handlerSpecific.sendResponse(responseHeaderMap, responseBodyMap);
                    }

                    @Override
                    public void onException(final OnExceptionContext context) {
                        responseBodyMap.put(EventMeshConstants.RET_CODE, EventMeshRetCode.EVENTMESH_SEND_ASYNC_MSG_ERR.getRetCode());
                        responseBodyMap.put(EventMeshConstants.RET_MSG, EventMeshRetCode.EVENTMESH_SEND_ASYNC_MSG_ERR.getErrMsg()
                            + EventMeshUtil.stackTrace(context.getException(), 2));
                        eventMeshHTTPServer.getHttpRetryer().newTimeout(sendMessageContext, 10, TimeUnit.SECONDS);
                        handlerSpecific.getTraceOperation().exceptionLatestTrace(context.getException(),
                            EventMeshUtil.getCloudEventExtensionMap(SpecVersion.V1.toString(), sendMessageContext.getEvent()));

                        handlerSpecific.sendResponse(responseHeaderMap, responseBodyMap);
                        LogUtils.error(log, "message|eventMesh2mq|REQ|ASYNC|send2MQCost={}ms|topic={}|bizSeqNo={}|uniqueId={}",
                            System.currentTimeMillis() - startTime, topic, bizNo, uniqueId, context.getException());
                    }
                });
            } else {
                LogUtils.error(log, "message|eventMesh2mq|REQ|ASYNC|send2MQCost={}ms|topic={}|bizSeqNo={}|uniqueId={}|apply filter failed",
                    System.currentTimeMillis() - startTime, topic, bizNo, uniqueId);
                handlerSpecific.getTraceOperation().endLatestTrace(sendMessageContext.getEvent());
                handlerSpecific.sendErrorResponse(EventMeshRetCode.EVENTMESH_FILTER_MSG_ERR, responseHeaderMap, responseBodyMap, EventMeshUtil.getCloudEventExtensionMap(SpecVersion.V1.toString(), event));
            }

        } catch (Exception ex) {
            eventMeshHTTPServer.getHttpRetryer().newTimeout(sendMessageContext, 10, TimeUnit.SECONDS);
            handlerSpecific.sendErrorResponse(EventMeshRetCode.EVENTMESH_SEND_ASYNC_MSG_ERR, responseHeaderMap, responseBodyMap, null);

            final long endTime = System.currentTimeMillis();
            LogUtils.error(log, "message|eventMesh2mq|REQ|ASYNC|send2MQCost={}ms|topic={}|bizSeqNo={}|uniqueId={}",
                endTime - startTime, topic, bizNo, uniqueId, ex);
        }
    }

    @Override
    public String[] paths() {
        return new String[]{RequestURI.PUBLISH.getRequestURI()};
    }
}
