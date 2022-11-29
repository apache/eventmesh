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
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.cloudevents.CloudEvent;
import io.cloudevents.SpecVersion;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpRequest;

import com.fasterxml.jackson.core.type.TypeReference;

@EventMeshTrace(isEnable = true)
public class SendAsyncRemoteEventProcessor implements AsyncHttpProcessor {

    public Logger messageLogger = LoggerFactory.getLogger(EventMeshConstants.MESSAGE);

    public Logger httpLogger = LoggerFactory.getLogger(EventMeshConstants.PROTOCOL_HTTP);

    public Logger aclLogger = LoggerFactory.getLogger(EventMeshConstants.ACL);

    private EventMeshHTTPServer eventMeshHTTPServer;

    public SendAsyncRemoteEventProcessor(EventMeshHTTPServer eventMeshHTTPServer) {
        this.eventMeshHTTPServer = eventMeshHTTPServer;
    }

    @Override
    public void handler(HandlerService.HandlerSpecific handlerSpecific, HttpRequest httpRequest) throws Exception {

        AsyncContext<HttpEventWrapper> asyncContext = handlerSpecific.getAsyncContext();

        ChannelHandlerContext ctx = handlerSpecific.getCtx();

        HttpEventWrapper requestWrapper = asyncContext.getRequest();

        httpLogger.info("uri={}|{}|client2eventMesh|from={}|to={}", requestWrapper.getRequestURI(),
            EventMeshConstants.PROTOCOL_HTTP, RemotingHelper.parseChannelRemoteAddr(ctx.channel()), IPUtils.getLocalAddress());

        // user request header
        Map<String, Object> requestHeaderMap = requestWrapper.getHeaderMap();
        String source = RemotingHelper.parseChannelRemoteAddr(ctx.channel());

        String env = eventMeshHTTPServer.getEventMeshHttpConfiguration().getEventMeshEnv();
        String idc = eventMeshHTTPServer.getEventMeshHttpConfiguration().getEventMeshIDC();
        String cluster = eventMeshHTTPServer.getEventMeshHttpConfiguration().getEventMeshCluster();
        String sysId = eventMeshHTTPServer.getEventMeshHttpConfiguration().getSysID();
        String meshGroup = env + "-" + idc + "-" + cluster + "-" + sysId;
        requestHeaderMap.put(ProtocolKey.ClientInstanceKey.IP, source);
        requestHeaderMap.put(ProtocolKey.ClientInstanceKey.ENV, eventMeshHTTPServer.getEventMeshHttpConfiguration().getEventMeshEnv());
        requestHeaderMap.put(ProtocolKey.ClientInstanceKey.IDC, eventMeshHTTPServer.getEventMeshHttpConfiguration().getEventMeshIDC());
        requestHeaderMap.put(ProtocolKey.ClientInstanceKey.SYS, eventMeshHTTPServer.getEventMeshHttpConfiguration().getSysID());
        requestHeaderMap.put(ProtocolKey.ClientInstanceKey.PRODUCERGROUP, meshGroup);

        // build sys header
        requestWrapper.buildSysHeaderForClient();

        // build cloudevents attributes
        requestHeaderMap.putIfAbsent("source", source);
        requestWrapper.buildSysHeaderForCE();

        // process remote event body
        Map<String, Object> bodyMap = JsonUtils.deserialize(new String(requestWrapper.getBody(), Constants.DEFAULT_CHARSET),
            new TypeReference<Map<String, Object>>() {
            });

        byte[] convertedBody = bodyMap.get("content").toString().getBytes(StandardCharsets.UTF_8);
        requestWrapper.setBody(convertedBody);

        String bizNo = requestHeaderMap.getOrDefault(ProtocolKey.ClientInstanceKey.BIZSEQNO, RandomStringUtils.generateNum(30)).toString();
        String uniqueId = requestHeaderMap.getOrDefault(ProtocolKey.ClientInstanceKey.UNIQUEID, RandomStringUtils.generateNum(30)).toString();
        String ttl = requestHeaderMap.getOrDefault(Constants.EVENTMESH_MESSAGE_CONST_TTL, 4 * 1000).toString();


        requestWrapper.getSysHeaderMap().putIfAbsent(ProtocolKey.ClientInstanceKey.BIZSEQNO, bizNo);
        requestWrapper.getSysHeaderMap().putIfAbsent(ProtocolKey.ClientInstanceKey.UNIQUEID, uniqueId);
        requestWrapper.getSysHeaderMap().putIfAbsent(Constants.EVENTMESH_MESSAGE_CONST_TTL, ttl);

        Map<String, Object> responseHeaderMap = new HashMap<>();
        responseHeaderMap.put(ProtocolKey.REQUEST_URI, requestWrapper.getRequestURI());
        responseHeaderMap.put(ProtocolKey.EventMeshInstanceKey.EVENTMESHCLUSTER,
                eventMeshHTTPServer.getEventMeshHttpConfiguration().getEventMeshCluster());
        responseHeaderMap.put(ProtocolKey.EventMeshInstanceKey.EVENTMESHIP, IPUtils.getLocalAddress());
        responseHeaderMap.put(ProtocolKey.EventMeshInstanceKey.EVENTMESHENV,
                eventMeshHTTPServer.getEventMeshHttpConfiguration().getEventMeshEnv());
        responseHeaderMap.put(ProtocolKey.EventMeshInstanceKey.EVENTMESHIDC,
                eventMeshHTTPServer.getEventMeshHttpConfiguration().getEventMeshIDC());

        Map<String, Object> responseBodyMap = new HashMap<>();

        Map<String, Object> sysHeaderMap = requestWrapper.getSysHeaderMap();
        Iterator<Map.Entry<String, Object>> it = requestHeaderMap.entrySet().iterator();
        while (it.hasNext()) {
            String key = it.next().getKey();
            if (sysHeaderMap.containsKey(key)) {
                it.remove();
            }
        }

        String protocolType = requestHeaderMap.getOrDefault(ProtocolKey.PROTOCOL_TYPE, "http").toString();

        ProtocolAdaptor<ProtocolTransportObject> httpProtocolAdaptor = ProtocolPluginFactory.getProtocolAdaptor(protocolType);
        CloudEvent event = httpProtocolAdaptor.toCloudEvent(requestWrapper);


        //validate event
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

        idc = event.getExtension(ProtocolKey.ClientInstanceKey.IDC) == null ? "" :
            event.getExtension(ProtocolKey.ClientInstanceKey.IDC).toString();
        String pid = event.getExtension(ProtocolKey.ClientInstanceKey.PID) == null ? "" :
            event.getExtension(ProtocolKey.ClientInstanceKey.PID).toString();
        String sys = event.getExtension(ProtocolKey.ClientInstanceKey.SYS) == null ? "" :
            event.getExtension(ProtocolKey.ClientInstanceKey.SYS).toString();

        //validate event-extension
        if (StringUtils.isBlank(idc)
            || StringUtils.isBlank(pid)
            || !StringUtils.isNumeric(pid)
            || StringUtils.isBlank(sys)) {
            handlerSpecific.sendErrorResponse(EventMeshRetCode.EVENTMESH_PROTOCOL_HEADER_ERR, responseHeaderMap,
                responseBodyMap, EventMeshUtil.getCloudEventExtensionMap(SpecVersion.V1.toString(), event));
            return;
        }

        String producerGroup = event.getExtension(ProtocolKey.ClientInstanceKey.PRODUCERGROUP) == null ? "" :
            event.getExtension(ProtocolKey.ClientInstanceKey.PRODUCERGROUP).toString();
        String topic = event.getSubject();

        //validate body
        if (StringUtils.isBlank(bizNo)
            || StringUtils.isBlank(uniqueId)
            || StringUtils.isBlank(producerGroup)
            || StringUtils.isBlank(topic)
            || event.getData() == null) {
            handlerSpecific.sendErrorResponse(EventMeshRetCode.EVENTMESH_PROTOCOL_BODY_ERR, responseHeaderMap,
                responseBodyMap, EventMeshUtil.getCloudEventExtensionMap(SpecVersion.V1.toString(), event));
            return;
        }

        //do acl check
        if (eventMeshHTTPServer.getEventMeshHttpConfiguration().isEventMeshServerSecurityEnable()) {
            String remoteAddr = RemotingHelper.parseChannelRemoteAddr(ctx.channel());
            String user = event.getExtension(ProtocolKey.ClientInstanceKey.USERNAME) == null ? "" :
                event.getExtension(ProtocolKey.ClientInstanceKey.USERNAME).toString();
            String pass = event.getExtension(ProtocolKey.ClientInstanceKey.PASSWD) == null ? "" :
                event.getExtension(ProtocolKey.ClientInstanceKey.PASSWD).toString();
            String subsystem =
                event.getExtension(ProtocolKey.ClientInstanceKey.SYS) == null ? "" : event.getExtension(ProtocolKey.ClientInstanceKey.SYS).toString();
            String requestURI = requestWrapper.getRequestURI();
            try {
                Acl.doAclCheckInHttpSend(remoteAddr, user, pass, subsystem, topic, requestURI);
            } catch (Exception e) {
                handlerSpecific.sendErrorResponse(EventMeshRetCode.EVENTMESH_ACL_ERR, responseHeaderMap,
                    responseBodyMap, EventMeshUtil.getCloudEventExtensionMap(SpecVersion.V1.toString(), event));
                aclLogger.warn("CLIENT HAS NO PERMISSION,SendAsyncMessageProcessor send failed", e);
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

        EventMeshProducer eventMeshProducer = eventMeshHTTPServer.getProducerManager().getEventMeshProducer(producerGroup);

        if (!eventMeshProducer.getStarted().get()) {
            handlerSpecific.sendErrorResponse(EventMeshRetCode.EVENTMESH_GROUP_PRODUCER_STOPED_ERR, responseHeaderMap,
                responseBodyMap, EventMeshUtil.getCloudEventExtensionMap(SpecVersion.V1.toString(), event));
            return;
        }

        String content = event.getData() == null ? "" : new String(event.getData().toBytes(), StandardCharsets.UTF_8);
        if (content.length() > eventMeshHTTPServer.getEventMeshHttpConfiguration().eventMeshEventSize) {
            httpLogger.error("Event size exceeds the limit: {}",
                eventMeshHTTPServer.getEventMeshHttpConfiguration().eventMeshEventSize);
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

            if (messageLogger.isDebugEnabled()) {
                messageLogger.debug("msg2MQMsg suc, bizSeqNo={}, topic={}", bizNo, topic);
            }
        } catch (Exception e) {
            messageLogger.error("msg2MQMsg err, bizSeqNo={}, topic={}", bizNo, topic, e);
            handlerSpecific.sendErrorResponse(EventMeshRetCode.EVENTMESH_PACKAGE_MSG_ERR, responseHeaderMap,
                responseBodyMap, EventMeshUtil.getCloudEventExtensionMap(SpecVersion.V1.toString(), event));
            return;
        }

        final SendMessageContext sendMessageContext = new SendMessageContext(bizNo, event, eventMeshProducer,
            eventMeshHTTPServer);
        eventMeshHTTPServer.metrics.getSummaryMetrics().recordSendMsg();

        long startTime = System.currentTimeMillis();

        try {
            event = CloudEventBuilder.from(sendMessageContext.getEvent())
                .withExtension(EventMeshConstants.REQ_EVENTMESH2MQ_TIMESTAMP, String.valueOf(System.currentTimeMillis()))
                .build();
            handlerSpecific.getTraceOperation().createClientTraceOperation(EventMeshUtil.getCloudEventExtensionMap(SpecVersion.V1.toString(), event),
                EventMeshTraceConstants.TRACE_UPSTREAM_EVENTMESH_CLIENT_SPAN, false);

            eventMeshProducer.send(sendMessageContext, new SendCallback() {

                @Override
                public void onSuccess(SendResult sendResult) {
                    responseBodyMap.put(EventMeshConstants.RET_CODE, EventMeshRetCode.SUCCESS.getRetCode());
                    responseBodyMap.put(EventMeshConstants.RET_MSG, EventMeshRetCode.SUCCESS.getErrMsg() + sendResult.toString());

                    messageLogger.info("message|eventMesh2mq|REQ|ASYNC|send2MQCost={}ms|topic={}|bizSeqNo={}|uniqueId={}",
                        System.currentTimeMillis() - startTime, topic, bizNo, uniqueId);
                    handlerSpecific.getTraceOperation().endLatestTrace(sendMessageContext.getEvent());
                    handlerSpecific.sendResponse(responseHeaderMap, responseBodyMap);
                }

                @Override
                public void onException(OnExceptionContext context) {
                    responseBodyMap.put(EventMeshConstants.RET_CODE, EventMeshRetCode.EVENTMESH_SEND_ASYNC_MSG_ERR.getRetCode());
                    responseBodyMap.put(EventMeshConstants.RET_MSG, EventMeshRetCode.EVENTMESH_SEND_ASYNC_MSG_ERR.getErrMsg()
                        + EventMeshUtil.stackTrace(context.getException(), 2));
                    eventMeshHTTPServer.getHttpRetryer().pushRetry(sendMessageContext.delay(10000));
                    handlerSpecific.getTraceOperation().exceptionLatestTrace(context.getException(),
                        EventMeshUtil.getCloudEventExtensionMap(SpecVersion.V1.toString(), sendMessageContext.getEvent()));

                    handlerSpecific.sendResponse(responseHeaderMap, responseBodyMap);
                    messageLogger.error("message|eventMesh2mq|REQ|ASYNC|send2MQCost={}ms|topic={}|bizSeqNo={}|uniqueId={}",
                        System.currentTimeMillis() - startTime, topic, bizNo, uniqueId, context.getException());
                }
            });
        } catch (Exception ex) {
            eventMeshHTTPServer.getHttpRetryer().pushRetry(sendMessageContext.delay(10000));
            handlerSpecific.sendErrorResponse(EventMeshRetCode.EVENTMESH_SEND_ASYNC_MSG_ERR, responseHeaderMap, responseBodyMap, null);

            long endTime = System.currentTimeMillis();
            messageLogger.error("message|eventMesh2mq|REQ|ASYNC|send2MQCost={}ms|topic={}|bizSeqNo={}|uniqueId={}",
                endTime - startTime, topic, bizNo, uniqueId, ex);
        }
    }

    @Override
    public String[] paths() {
        return new String[] {RequestURI.PUBLISH_BRIDGE.getRequestURI()};
    }

}
