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

import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.protocol.SubscriptionItem;
import org.apache.eventmesh.common.protocol.http.HttpEventWrapper;
import org.apache.eventmesh.common.protocol.http.common.EventMeshRetCode;
import org.apache.eventmesh.common.protocol.http.common.ProtocolKey;
import org.apache.eventmesh.common.protocol.http.common.RequestURI;
import org.apache.eventmesh.common.utils.IPUtils;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.runtime.acl.Acl;
import org.apache.eventmesh.runtime.boot.EventMeshHTTPServer;
import org.apache.eventmesh.runtime.common.EventMeshTrace;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.consumer.ClientInfo;
import org.apache.eventmesh.runtime.core.consumer.SubscriptionManager;
import org.apache.eventmesh.runtime.core.protocol.http.processor.inf.AbstractEventProcessor;
import org.apache.eventmesh.runtime.util.RemotingHelper;
import org.apache.eventmesh.runtime.util.WebhookUtil;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;

import io.netty.channel.Channel;
import io.netty.handler.codec.http.HttpRequest;

import com.fasterxml.jackson.core.type.TypeReference;

import lombok.extern.slf4j.Slf4j;

@EventMeshTrace
@Slf4j
public class LocalSubscribeEventProcessor extends AbstractEventProcessor {

    private final Acl acl;

    public LocalSubscribeEventProcessor(final EventMeshHTTPServer eventMeshHTTPServer) {
        super(eventMeshHTTPServer);
        this.acl = eventMeshHTTPServer.getAcl();
    }

    @Override
    public void handler(final HandlerService.HandlerSpecific handlerSpecific, final HttpRequest httpRequest)
        throws Exception {

        final Channel channel = handlerSpecific.getCtx().channel();
        final HttpEventWrapper requestWrapper = handlerSpecific.getAsyncContext().getRequest();
        String localAddress = IPUtils.getLocalAddress();
        String remoteAddr = RemotingHelper.parseChannelRemoteAddr(channel);
        log.info("uri={}|{}|client2eventMesh|from={}|to={}",
            requestWrapper.getRequestURI(), EventMeshConstants.PROTOCOL_HTTP, remoteAddr, localAddress);

        // user request header
        requestWrapper.getHeaderMap().put(ProtocolKey.ClientInstanceKey.IP.getKey(), remoteAddr);
        // build sys header
        requestWrapper.buildSysHeaderForClient();

        final Map<String, Object> responseHeaderMap = builderResponseHeaderMap(requestWrapper);
        final Map<String, Object> sysHeaderMap = requestWrapper.getSysHeaderMap();
        final Map<String, Object> responseBodyMap = new HashMap<>();

        // validate header
        if (validateSysHeader(sysHeaderMap)) {
            handlerSpecific.sendErrorResponse(EventMeshRetCode.EVENTMESH_PROTOCOL_HEADER_ERR, responseHeaderMap,
                responseBodyMap, null);
            return;
        }

        // validate body
        final Map<String, Object> requestBodyMap = Optional.ofNullable(JsonUtils.parseTypeReferenceObject(
            new String(requestWrapper.getBody(), Constants.DEFAULT_CHARSET),
            new TypeReference<HashMap<String, Object>>() {
            })).orElseGet(HashMap::new);

        if (validatedRequestBodyMap(requestBodyMap)) {
            handlerSpecific.sendErrorResponse(EventMeshRetCode.EVENTMESH_PROTOCOL_BODY_ERR, responseHeaderMap,
                responseBodyMap, null);
            return;
        }

        final String url = requestBodyMap.get("url").toString();
        final String consumerGroup = requestBodyMap.get("consumerGroup").toString();
        final String topic = JsonUtils.toJSONString(requestBodyMap.get("topic"));

        // SubscriptionItem
        final List<SubscriptionItem> subscriptionList = Optional.ofNullable(JsonUtils.parseTypeReferenceObject(
            topic,
            new TypeReference<List<SubscriptionItem>>() {
            })).orElseGet(Collections::emptyList);

        // do acl check
        if (eventMeshHTTPServer.getEventMeshHttpConfiguration().isEventMeshServerSecurityEnable()) {
            for (final SubscriptionItem item : subscriptionList) {
                try {
                    String user = sysHeaderMap.get(ProtocolKey.ClientInstanceKey.USERNAME.getKey()).toString();
                    String pass = sysHeaderMap.get(ProtocolKey.ClientInstanceKey.PASSWD.getKey()).toString();
                    String subsystem = sysHeaderMap.get(ProtocolKey.ClientInstanceKey.SYS.getKey()).toString();
                    this.acl.doAclCheckInHttpReceive(remoteAddr, user, pass, subsystem, item.getTopic(),
                        requestWrapper.getRequestURI());
                } catch (Exception e) {
                    log.warn("CLIENT HAS NO PERMISSION,SubscribeProcessor subscribe failed", e);

                    handlerSpecific.sendErrorResponse(EventMeshRetCode.EVENTMESH_ACL_ERR, responseHeaderMap,
                        responseBodyMap, null);
                    return;
                }
            }
        }

        // validate URL
        try {
            if (!IPUtils.isValidDomainOrIp(url, eventMeshHTTPServer.getEventMeshHttpConfiguration().getEventMeshIpv4BlackList(),
                eventMeshHTTPServer.getEventMeshHttpConfiguration().getEventMeshIpv6BlackList())) {
                log.error("subscriber url {} is not valid", url);

                handlerSpecific.sendErrorResponse(EventMeshRetCode.EVENTMESH_PROTOCOL_BODY_ERR, responseHeaderMap,
                    responseBodyMap, null);
                return;
            }
        } catch (Exception e) {
            log.error("subscriber url {} is not valid", url, e);

            handlerSpecific.sendErrorResponse(EventMeshRetCode.EVENTMESH_PROTOCOL_BODY_ERR, responseHeaderMap,
                responseBodyMap, null);
            return;
        }

        // obtain webhook delivery agreement for Abuse Protection
        if (!WebhookUtil.obtainDeliveryAgreement(eventMeshHTTPServer.getHttpClientPool().getClient(),
            url, eventMeshHTTPServer.getEventMeshHttpConfiguration().getEventMeshWebhookOrigin())) {
            log.error("subscriber url {} is not allowed by the target system", url);
            handlerSpecific.sendErrorResponse(EventMeshRetCode.EVENTMESH_PROTOCOL_BODY_ERR, responseHeaderMap,
                responseBodyMap, null);
            return;
        }

        ClientInfo clientInfo = getClientInfo(requestWrapper);
        SubscriptionManager subscriptionManager = eventMeshHTTPServer.getSubscriptionManager();
        subscriptionManager.registerClient(clientInfo, consumerGroup, subscriptionList, url);
        subscriptionManager.updateSubscription(clientInfo, consumerGroup, url, subscriptionList);

        final long startTime = System.currentTimeMillis();
        try {
            // subscription relationship change notification
            eventMeshHTTPServer.getConsumerManager().notifyConsumerManager(consumerGroup,
                eventMeshHTTPServer.getSubscriptionManager().getLocalConsumerGroupMapping().get(consumerGroup));
            responseBodyMap.put(EventMeshConstants.RET_CODE, EventMeshRetCode.SUCCESS.getRetCode());
            responseBodyMap.put(EventMeshConstants.RET_MSG, EventMeshRetCode.SUCCESS.getErrMsg());

            handlerSpecific.sendResponse(responseHeaderMap, responseBodyMap);

        } catch (Exception e) {
            log.error("message|eventMesh2mq|REQ|ASYNC|send2MQCost={}ms|topic={}|url={}",
                System.currentTimeMillis() - startTime, JsonUtils.toJSONString(subscriptionList), url, e);

            handlerSpecific.sendErrorResponse(EventMeshRetCode.EVENTMESH_SUBSCRIBE_ERR, responseHeaderMap, responseBodyMap, null);
        }

        // Update service metadata
        eventMeshHTTPServer.getSubscriptionManager().updateMetaData();


    }

    @Override
    public String[] paths() {
        return new String[] {RequestURI.SUBSCRIBE_LOCAL.getRequestURI()};
    }

    @Override
    public Executor executor() {
        return eventMeshHTTPServer.getHttpThreadPoolGroup().getClientManageExecutor();

    }

    private ClientInfo getClientInfo(final HttpEventWrapper requestWrapper) {
        final Map<String, Object> requestHeaderMap = requestWrapper.getSysHeaderMap();
        ClientInfo clientInfo = new ClientInfo();
        clientInfo.setEnv(requestHeaderMap.get(ProtocolKey.ClientInstanceKey.ENV.getKey()).toString());
        clientInfo.setIdc(requestHeaderMap.get(ProtocolKey.ClientInstanceKey.IDC.getKey()).toString());
        clientInfo.setSys(requestHeaderMap.get(ProtocolKey.ClientInstanceKey.SYS.getKey()).toString());
        clientInfo.setIp(requestHeaderMap.get(ProtocolKey.ClientInstanceKey.IP.getKey()).toString());
        clientInfo.setPid(requestHeaderMap.get(ProtocolKey.ClientInstanceKey.PID.getKey()).toString());
        return clientInfo;
    }
}
