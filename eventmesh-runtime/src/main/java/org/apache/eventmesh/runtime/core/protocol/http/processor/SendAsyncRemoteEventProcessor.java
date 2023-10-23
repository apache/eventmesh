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
import org.apache.eventmesh.common.protocol.http.HttpEventWrapper;
import org.apache.eventmesh.common.protocol.http.common.EventMeshRetCode;
import org.apache.eventmesh.common.protocol.http.common.ProtocolKey;
import org.apache.eventmesh.common.protocol.http.common.RequestURI;
import org.apache.eventmesh.common.utils.IPUtils;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.common.utils.RandomStringUtils;
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
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import io.cloudevents.CloudEvent;
import io.cloudevents.SpecVersion;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpRequest;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.Maps;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@EventMeshTrace(isEnable = true)
public class SendAsyncRemoteEventProcessor implements AsyncHttpProcessor {

    private final transient EventMeshHTTPServer eventMeshHTTPServer;

    private final Acl acl;

    public SendAsyncRemoteEventProcessor(final EventMeshHTTPServer eventMeshHTTPServer) {
        this.eventMeshHTTPServer = eventMeshHTTPServer;
        this.acl = eventMeshHTTPServer.getAcl();
    }

    @Override
    public void handler(final HandlerService.HandlerSpecific handlerSpecific, final HttpRequest httpRequest) throws Exception {

        final AsyncContext<HttpEventWrapper> asyncContext = handlerSpecific.getAsyncContext();

        final ChannelHandlerContext ctx = handlerSpecific.getCtx();

        final HttpEventWrapper requestWrapper = asyncContext.getRequest();

        final String localAddress = IPUtils.getLocalAddress();
        if (log.isInfoEnabled()) {
            log.info("uri={}|{}|client2eventMesh|from={}|to={}", requestWrapper.getRequestURI(),
                EventMeshConstants.PROTOCOL_HTTP, RemotingHelper.parseChannelRemoteAddr(ctx.channel()), localAddress);
        }

        // user request header
        final Map<String, Object> requestHeaderMap = requestWrapper.getHeaderMap();
        final String source = RemotingHelper.parseChannelRemoteAddr(ctx.channel());

        final String env = eventMeshHTTPServer.getEventMeshHttpConfiguration().getEventMeshEnv();
        final String meshGroup = new StringBuilder()
            .append(env)
            .append('-')
            .append(eventMeshHTTPServer.getEventMeshHttpConfiguration().getEventMeshIDC())
            .append('-')
            .append(eventMeshHTTPServer.getEventMeshHttpConfiguration().getEventMeshCluster())
            .append('-')
            .append(eventMeshHTTPServer.getEventMeshHttpConfiguration().getSysID())
            .toString();
        requestHeaderMap.put(ProtocolKey.ClientInstanceKey.IP.getKey(), source);
        requestHeaderMap.put(ProtocolKey.ClientInstanceKey.ENV.getKey(),
            eventMeshHTTPServer.getEventMeshHttpConfiguration().getEventMeshEnv());
        requestHeaderMap.put(ProtocolKey.ClientInstanceKey.IDC.getKey(),
            eventMeshHTTPServer.getEventMeshHttpConfiguration().getEventMeshIDC());
        requestHeaderMap.put(ProtocolKey.ClientInstanceKey.SYS.getKey(),
            eventMeshHTTPServer.getEventMeshHttpConfiguration().getSysID());
        requestHeaderMap.put(ProtocolKey.ClientInstanceKey.PRODUCERGROUP.getKey(), meshGroup);

        // build sys header
        requestWrapper.buildSysHeaderForClient();

        // build cloudevents attributes
        requestHeaderMap.putIfAbsent("source", source);
        requestWrapper.buildSysHeaderForCE();

        // process remote event body
        final Map<String, Object> bodyMap = Optional.ofNullable(JsonUtils.parseTypeReferenceObject(
            new String(requestWrapper.getBody(), Constants.DEFAULT_CHARSET),
            new TypeReference<Map<String, Object>>() {
            }

        )).orElseGet(Maps::newHashMap);

        requestWrapper.setBody(bodyMap.get("content").toString().getBytes(StandardCharsets.UTF_8));

        final String bizNo = requestHeaderMap.getOrDefault(ProtocolKey.ClientInstanceKey.BIZSEQNO.getKey(),
            RandomStringUtils.generateNum(30)).toString();
        final String uniqueId = requestHeaderMap.getOrDefault(ProtocolKey.ClientInstanceKey.UNIQUEID.getKey(),
            RandomStringUtils.generateNum(30)).toString();
        final String ttl = requestHeaderMap.getOrDefault(Constants.EVENTMESH_MESSAGE_CONST_TTL,
            4 * 1000).toString();

        requestWrapper.getSysHeaderMap().putIfAbsent(ProtocolKey.ClientInstanceKey.BIZSEQNO.getKey(), bizNo);
        requestWrapper.getSysHeaderMap().putIfAbsent(ProtocolKey.ClientInstanceKey.UNIQUEID.getKey(), uniqueId);
        requestWrapper.getSysHeaderMap().putIfAbsent(Constants.EVENTMESH_MESSAGE_CONST_TTL, ttl);

        final Map<String, Object> responseHeaderMap = new HashMap<>();
        responseHeaderMap.put(ProtocolKey.REQUEST_URI, requestWrapper.getRequestURI());
        responseHeaderMap.put(ProtocolKey.EventMeshInstanceKey.EVENTMESHCLUSTER,
            eventMeshHTTPServer.getEventMeshHttpConfiguration().getEventMeshCluster());
        responseHeaderMap.put(ProtocolKey.EventMeshInstanceKey.EVENTMESHIP, localAddress);
        responseHeaderMap.put(ProtocolKey.EventMeshInstanceKey.EVENTMESHENV,
            eventMeshHTTPServer.getEventMeshHttpConfiguration().getEventMeshEnv());
        responseHeaderMap.put(ProtocolKey.EventMeshInstanceKey.EVENTMESHIDC,
            eventMeshHTTPServer.getEventMeshHttpConfiguration().getEventMeshIDC());

        final Map<String, Object> responseBodyMap = new HashMap<>();
        final Map<String, Object> sysHeaderMap = requestWrapper.getSysHeaderMap();
        final Iterator<Map.Entry<String, Object>> it = requestHeaderMap.entrySet().iterator();
        while (it.hasNext()) {
            final String key = it.next().getKey();
            if (sysHeaderMap.containsKey(key)) {
                it.remove();
            }
        }

        final String protocolType = requestHeaderMap.getOrDefault(ProtocolKey.PROTOCOL_TYPE, "http").toString();

        final ProtocolAdaptor<ProtocolTransportObject> httpProtocolAdaptor = ProtocolPluginFactory.getProtocolAdaptor(protocolType);
        CloudEvent event = httpProtocolAdaptor.toCloudEvent(requestWrapper);

        // validate event
        if (event == null
            || StringUtils.isBlank(event.getId())
            || event.getSource() == null
            || event.getSpecVersion() == null
            || StringUtils.isBlank(event.getType())
            || StringUtils.isBlank(event.getSubject())) {

            handlerSpecific.sendErrorResponse(EventMeshRetCode.EVENTMESH_PROTOCOL_HEADER_ERR, responseHeaderMap,
                responseBodyMap, EventMeshUtil.getCloudEventExtensionMap(SpecVersion.V1.toString(), event));

            return;
        }

        final String pid = getExtension(event, ProtocolKey.ClientInstanceKey.PID.getKey());
        final String sys = getExtension(event, ProtocolKey.ClientInstanceKey.SYS.getKey());

        // validate event-extension
        if (StringUtils.isBlank(getExtension(event, ProtocolKey.ClientInstanceKey.IDC.getKey()))
            || StringUtils.isBlank(pid)
            || !StringUtils.isNumeric(pid)
            || StringUtils.isBlank(sys)) {
            handlerSpecific.sendErrorResponse(EventMeshRetCode.EVENTMESH_PROTOCOL_HEADER_ERR, responseHeaderMap,
                responseBodyMap, EventMeshUtil.getCloudEventExtensionMap(SpecVersion.V1.toString(), event));
            return;
        }

        final String producerGroup = getExtension(event, ProtocolKey.ClientInstanceKey.PRODUCERGROUP.getKey());
        final String topic = event.getSubject();

        // validate body
        if (StringUtils.isBlank(bizNo)
            || StringUtils.isBlank(uniqueId)
            || StringUtils.isBlank(producerGroup)
            || StringUtils.isBlank(topic)
            || event.getData() == null) {
            handlerSpecific.sendErrorResponse(EventMeshRetCode.EVENTMESH_PROTOCOL_BODY_ERR, responseHeaderMap,
                responseBodyMap, EventMeshUtil.getCloudEventExtensionMap(SpecVersion.V1.toString(), event));
            return;
        }

        // do acl check
        if (eventMeshHTTPServer.getEventMeshHttpConfiguration().isEventMeshServerSecurityEnable()) {
            try {
                this.acl.doAclCheckInHttpSend(RemotingHelper.parseChannelRemoteAddr(ctx.channel()),
                    getExtension(event, ProtocolKey.ClientInstanceKey.USERNAME.getKey()),
                    getExtension(event, ProtocolKey.ClientInstanceKey.PASSWD.getKey()),
                    getExtension(event, ProtocolKey.ClientInstanceKey.SYS.getKey()),
                    topic,
                    requestWrapper.getRequestURI());
            } catch (Exception e) {
                handlerSpecific.sendErrorResponse(EventMeshRetCode.EVENTMESH_ACL_ERR, responseHeaderMap,
                    responseBodyMap, EventMeshUtil.getCloudEventExtensionMap(SpecVersion.V1.toString(), event));

                log.error("CLIENT HAS NO PERMISSION,SendAsyncMessageProcessor send failed", e);
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

        final EventMeshProducer eventMeshProducer = eventMeshHTTPServer.getProducerManager().getEventMeshProducer(producerGroup);

        if (!eventMeshProducer.isStarted()) {
            handlerSpecific.sendErrorResponse(EventMeshRetCode.EVENTMESH_GROUP_PRODUCER_STOPED_ERR, responseHeaderMap,
                responseBodyMap, EventMeshUtil.getCloudEventExtensionMap(SpecVersion.V1.toString(), event));
            return;
        }

        final String content = event.getData() == null ? "" : new String(event.getData().toBytes(), StandardCharsets.UTF_8);
        if (content.length() > eventMeshHTTPServer.getEventMeshHttpConfiguration().getEventMeshEventSize()) {
            if (log.isErrorEnabled()) {
                log.error("Event size exceeds the limit: {}",
                    eventMeshHTTPServer.getEventMeshHttpConfiguration().getEventMeshEventSize());
            }
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

            if (log.isDebugEnabled()) {
                log.debug("msg2MQMsg suc, bizSeqNo={}, topic={}", bizNo, topic);
            }
        } catch (Exception e) {
            if (log.isErrorEnabled()) {
                log.error("msg2MQMsg err, bizSeqNo={}, topic={}", bizNo, topic, e);
            }
            handlerSpecific.sendErrorResponse(EventMeshRetCode.EVENTMESH_PACKAGE_MSG_ERR, responseHeaderMap,
                responseBodyMap, EventMeshUtil.getCloudEventExtensionMap(SpecVersion.V1.toString(), event));
            return;
        }

        final SendMessageContext sendMessageContext = new SendMessageContext(bizNo, event, eventMeshProducer,
            eventMeshHTTPServer);
        eventMeshHTTPServer.getMetrics().getSummaryMetrics().recordSendMsg();

        final long startTime = System.currentTimeMillis();

        try {
            event = CloudEventBuilder.from(sendMessageContext.getEvent())
                .withExtension(EventMeshConstants.REQ_EVENTMESH2MQ_TIMESTAMP, String.valueOf(System.currentTimeMillis()))
                .build();
            handlerSpecific.getTraceOperation().createClientTraceOperation(EventMeshUtil.getCloudEventExtensionMap(SpecVersion.V1.toString(), event),
                EventMeshTraceConstants.TRACE_UPSTREAM_EVENTMESH_CLIENT_SPAN, false);

            eventMeshProducer.send(sendMessageContext, new SendCallback() {

                @Override
                public void onSuccess(final SendResult sendResult) {
                    responseBodyMap.put(EventMeshConstants.RET_CODE, EventMeshRetCode.SUCCESS.getRetCode());
                    responseBodyMap.put(EventMeshConstants.RET_MSG, EventMeshRetCode.SUCCESS.getErrMsg() + sendResult);

                    if (log.isInfoEnabled()) {
                        log.info("message|eventMesh2mq|REQ|ASYNC|send2MQCost={}ms|topic={}|bizSeqNo={}|uniqueId={}",
                            System.currentTimeMillis() - startTime, topic, bizNo, uniqueId);
                    }
                    handlerSpecific.getTraceOperation().endLatestTrace(sendMessageContext.getEvent());
                    handlerSpecific.sendResponse(responseHeaderMap, responseBodyMap);
                }

                @Override
                public void onException(final OnExceptionContext context) {
                    responseBodyMap.put(EventMeshConstants.RET_CODE, EventMeshRetCode.EVENTMESH_SEND_ASYNC_MSG_ERR.getRetCode());
                    responseBodyMap.put(EventMeshConstants.RET_MSG, EventMeshRetCode.EVENTMESH_SEND_ASYNC_MSG_ERR.getErrMsg()
                        + EventMeshUtil.stackTrace(context.getException(), 2));
                    eventMeshHTTPServer.getHttpRetryer().pushRetry(sendMessageContext.delay(10_000));
                    handlerSpecific.getTraceOperation().exceptionLatestTrace(context.getException(),
                        EventMeshUtil.getCloudEventExtensionMap(SpecVersion.V1.toString(), sendMessageContext.getEvent()));

                    handlerSpecific.sendResponse(responseHeaderMap, responseBodyMap);

                    if (log.isErrorEnabled()) {
                        log.error("message|eventMesh2mq|REQ|ASYNC|send2MQCost={}ms|topic={}|bizSeqNo={}|uniqueId={}",
                            System.currentTimeMillis() - startTime, topic, bizNo, uniqueId, context.getException());
                    }
                }
            });
        } catch (Exception ex) {
            eventMeshHTTPServer.getHttpRetryer().pushRetry(sendMessageContext.delay(10_000));
            handlerSpecific.sendErrorResponse(EventMeshRetCode.EVENTMESH_SEND_ASYNC_MSG_ERR, responseHeaderMap,
                responseBodyMap, null);

            if (log.isErrorEnabled()) {
                log.error("message|eventMesh2mq|REQ|ASYNC|send2MQCost={}ms|topic={}|bizSeqNo={}|uniqueId={}",
                    System.currentTimeMillis() - startTime, topic, bizNo, uniqueId, ex);
            }
        }
    }

    private String getExtension(final CloudEvent event, final String protocolKey) {
        return Optional.ofNullable(event.getExtension(protocolKey))
            .map(Objects::toString)
            .orElseGet(() -> "");
    }

    @Override
    public String[] paths() {
        return new String[]{RequestURI.PUBLISH_BRIDGE.getRequestURI()};
    }

}
