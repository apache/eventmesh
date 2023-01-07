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
import org.apache.eventmesh.runtime.core.consumergroup.ConsumerGroupConf;
import org.apache.eventmesh.runtime.core.consumergroup.ConsumerGroupTopicConf;
import org.apache.eventmesh.runtime.core.protocol.http.processor.inf.AbstractEventProcessor;
import org.apache.eventmesh.runtime.core.protocol.http.processor.inf.Client;
import org.apache.eventmesh.runtime.util.RemotingHelper;
import org.apache.eventmesh.runtime.util.WebhookUtil;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.netty.channel.Channel;
import io.netty.handler.codec.http.HttpRequest;

import com.fasterxml.jackson.core.type.TypeReference;

import lombok.extern.slf4j.Slf4j;


@EventMeshTrace
@Slf4j
public class LocalSubscribeEventProcessor extends AbstractEventProcessor {
    
    public LocalSubscribeEventProcessor(final EventMeshHTTPServer eventMeshHTTPServer) {
        super(eventMeshHTTPServer);
    }

    @Override
    public void handler(final HandlerService.HandlerSpecific handlerSpecific, final HttpRequest httpRequest)
            throws Exception {

        final Channel channel = handlerSpecific.getCtx().channel();
        final HttpEventWrapper requestWrapper = handlerSpecific.getAsyncContext().getRequest();

        if (log.isInfoEnabled()) {
            log.info("uri={}|{}|client2eventMesh|from={}|to={}", requestWrapper.getRequestURI(),
                    EventMeshConstants.PROTOCOL_HTTP, RemotingHelper.parseChannelRemoteAddr(channel),
                    IPUtils.getLocalAddress());
        }

        // user request header
        requestWrapper.getHeaderMap().put(ProtocolKey.ClientInstanceKey.IP,
                RemotingHelper.parseChannelRemoteAddr(channel));
        // build sys header
        requestWrapper.buildSysHeaderForClient();

        final Map<String, Object> responseHeaderMap = builderResponseHeaderMap(requestWrapper);
        final Map<String, Object> sysHeaderMap = requestWrapper.getSysHeaderMap();
        final Map<String, Object> responseBodyMap = new HashMap<>();

        //validate header
        if (validateSysHeader(sysHeaderMap)) {
            handlerSpecific.sendErrorResponse(EventMeshRetCode.EVENTMESH_PROTOCOL_HEADER_ERR, responseHeaderMap,
                    responseBodyMap, null);
            return;
        }

        //validate body
        final Map<String, Object> requestBodyMap = Optional.ofNullable(JsonUtils.deserialize(
                new String(requestWrapper.getBody(), Constants.DEFAULT_CHARSET),
                new TypeReference<HashMap<String, Object>>() {
                }
        )).orElseGet(HashMap::new);

        if (validatedRequestBodyMap(requestBodyMap)) {
            handlerSpecific.sendErrorResponse(EventMeshRetCode.EVENTMESH_PROTOCOL_BODY_ERR, responseHeaderMap,
                    responseBodyMap, null);
            return;
        }

        final String url = requestBodyMap.get("url").toString();
        final String consumerGroup = requestBodyMap.get("consumerGroup").toString();
        final String topic = JsonUtils.serialize(requestBodyMap.get("topic"));

        // SubscriptionItem
        final List<SubscriptionItem> subscriptionList = Optional.ofNullable(JsonUtils.deserialize(
                topic,
                new TypeReference<List<SubscriptionItem>>() {
                }
        )).orElseGet(Collections::emptyList);

        //do acl check
        if (eventMeshHTTPServer.getEventMeshHttpConfiguration().isEventMeshServerSecurityEnable()) {
            for (final SubscriptionItem item : subscriptionList) {
                try {
                    Acl.doAclCheckInHttpReceive(RemotingHelper.parseChannelRemoteAddr(channel),
                            sysHeaderMap.get(ProtocolKey.ClientInstanceKey.USERNAME).toString(),
                            sysHeaderMap.get(ProtocolKey.ClientInstanceKey.PASSWD).toString(),
                            sysHeaderMap.get(ProtocolKey.ClientInstanceKey.SYS).toString(),
                            item.getTopic(),
                            requestWrapper.getRequestURI());
                } catch (Exception e) {
                    if (log.isWarnEnabled()) {
                        log.warn("CLIENT HAS NO PERMISSION,SubscribeProcessor subscribe failed", e);
                    }

                    handlerSpecific.sendErrorResponse(EventMeshRetCode.EVENTMESH_ACL_ERR, responseHeaderMap,
                            responseBodyMap, null);
                    return;
                }
            }
        }

        // validate URL
        try {
            if (!IPUtils.isValidDomainOrIp(url, eventMeshHTTPServer.getEventMeshHttpConfiguration().eventMeshIpv4BlackList,
                    eventMeshHTTPServer.getEventMeshHttpConfiguration().eventMeshIpv6BlackList)) {
                if (log.isErrorEnabled()) {
                    log.error("subscriber url {} is not valid", url);
                }
                
                handlerSpecific.sendErrorResponse(EventMeshRetCode.EVENTMESH_PROTOCOL_BODY_ERR, responseHeaderMap,
                        responseBodyMap, null);
                return;
            }
        } catch (Exception e) {
            if (log.isErrorEnabled()) {
                log.error("subscriber url {} is not valid", url, e);
            }

            handlerSpecific.sendErrorResponse(EventMeshRetCode.EVENTMESH_PROTOCOL_BODY_ERR, responseHeaderMap,
                    responseBodyMap, null);
            return;
        }

        // obtain webhook delivery agreement for Abuse Protection
        if (!WebhookUtil.obtainDeliveryAgreement(eventMeshHTTPServer.httpClientPool.getClient(),
                url, eventMeshHTTPServer.getEventMeshHttpConfiguration().getEventMeshWebhookOrigin())) {
            if (log.isErrorEnabled()) {
                log.error("subscriber url {} is not allowed by the target system", url);
            }
            handlerSpecific.sendErrorResponse(EventMeshRetCode.EVENTMESH_PROTOCOL_BODY_ERR, responseHeaderMap,
                    responseBodyMap, null);
            return;
        }

        synchronized (eventMeshHTTPServer.localClientInfoMapping) {

            registerClient(requestWrapper, consumerGroup, subscriptionList, url);

            for (final SubscriptionItem subTopic : subscriptionList) {
                final List<Client> groupTopicClients = eventMeshHTTPServer.localClientInfoMapping
                        .get(consumerGroup + "@" + subTopic.getTopic());

                if (CollectionUtils.isEmpty(groupTopicClients)) {
                    if (log.isErrorEnabled()) {
                        log.error("group {} topic {} clients is empty", consumerGroup, subTopic);
                    }
                }

                final Map<String, List<String>> idcUrls = new HashMap<>();
                for (final Client client : groupTopicClients) {
                    if (idcUrls.containsKey(client.getIdc())) {
                        idcUrls.get(client.getIdc()).add(StringUtils.deleteWhitespace(client.getUrl()));
                    } else {
                        final List<String> urls = new ArrayList<>();
                        urls.add(client.getUrl());
                        idcUrls.put(client.getIdc(), urls);
                    }
                }

                ConsumerGroupConf consumerGroupConf =
                        eventMeshHTTPServer.localConsumerGroupMapping.get(consumerGroup);
                if (consumerGroupConf == null) {
                    // new subscription
                    consumerGroupConf = new ConsumerGroupConf(consumerGroup);
                    final ConsumerGroupTopicConf consumeTopicConfig = new ConsumerGroupTopicConf();
                    consumeTopicConfig.setConsumerGroup(consumerGroup);
                    consumeTopicConfig.setTopic(subTopic.getTopic());
                    consumeTopicConfig.setSubscriptionItem(subTopic);
                    consumeTopicConfig.setUrls(new HashSet<>(Collections.singletonList(url)));
                    consumeTopicConfig.setIdcUrls(idcUrls);

                    final Map<String, ConsumerGroupTopicConf> map = new HashMap<>();
                    map.put(subTopic.getTopic(), consumeTopicConfig);
                    consumerGroupConf.setConsumerGroupTopicConf(map);
                } else {
                    // already subscribed
                    final Map<String, ConsumerGroupTopicConf> map =
                            consumerGroupConf.getConsumerGroupTopicConf();
                    if (!map.containsKey(subTopic.getTopic())) {
                        //If there are multiple topics, append it
                        final ConsumerGroupTopicConf newTopicConf = new ConsumerGroupTopicConf();
                        newTopicConf.setConsumerGroup(consumerGroup);
                        newTopicConf.setTopic(subTopic.getTopic());
                        newTopicConf.setSubscriptionItem(subTopic);
                        newTopicConf.setUrls(new HashSet<>(Collections.singletonList(url)));
                        newTopicConf.setIdcUrls(idcUrls);
                        map.put(subTopic.getTopic(), newTopicConf);
                    }

                    for (final Map.Entry<String, ConsumerGroupTopicConf> set : map.entrySet()) {
                        if (!StringUtils.equals(subTopic.getTopic(), set.getKey())) {
                            continue;
                        }

                        final ConsumerGroupTopicConf latestTopicConf = new ConsumerGroupTopicConf();
                        latestTopicConf.setConsumerGroup(consumerGroup);
                        latestTopicConf.setTopic(subTopic.getTopic());
                        latestTopicConf.setSubscriptionItem(subTopic);
                        latestTopicConf.setUrls(new HashSet<>(Collections.singletonList(url)));

                        final ConsumerGroupTopicConf currentTopicConf = set.getValue();
                        latestTopicConf.getUrls().addAll(currentTopicConf.getUrls());
                        latestTopicConf.setIdcUrls(idcUrls);

                        map.put(set.getKey(), latestTopicConf);
                    }
                }
                eventMeshHTTPServer.localConsumerGroupMapping.put(consumerGroup, consumerGroupConf);
            }

            final long startTime = System.currentTimeMillis();
            try {
                // subscription relationship change notification
                eventMeshHTTPServer.getConsumerManager().notifyConsumerManager(consumerGroup,
                        eventMeshHTTPServer.localConsumerGroupMapping.get(consumerGroup));
                responseBodyMap.put("retCode", EventMeshRetCode.SUCCESS.getRetCode());
                responseBodyMap.put("retMsg", EventMeshRetCode.SUCCESS.getErrMsg());

                handlerSpecific.sendResponse(responseHeaderMap, responseBodyMap);

            } catch (Exception e) {
                if (log.isErrorEnabled()) {
                    log.error("message|eventMesh2mq|REQ|ASYNC|send2MQCost={}ms|topic={}|url={}",
                            System.currentTimeMillis() - startTime,
                            JsonUtils.serialize(subscriptionList),
                            url, e);
                }

                handlerSpecific.sendErrorResponse(EventMeshRetCode.EVENTMESH_SUBSCRIBE_ERR, responseHeaderMap,
                        responseBodyMap, null);
            }

            // Update service metadata
            updateMetadata();
        }

    }

    @Override
    public String[] paths() {
        return new String[]{RequestURI.SUBSCRIBE_LOCAL.getRequestURI()};
    }

    private void registerClient(final HttpEventWrapper requestWrapper, final String consumerGroup,
                                final List<SubscriptionItem> subscriptionItems, final String url) {
        final Map<String, Object> requestHeaderMap = requestWrapper.getSysHeaderMap();
        for (final SubscriptionItem item : subscriptionItems) {
            final Client client = new Client();
            client.setEnv(requestHeaderMap.get(ProtocolKey.ClientInstanceKey.ENV).toString());
            client.setIdc(requestHeaderMap.get(ProtocolKey.ClientInstanceKey.IDC).toString());
            client.setSys(requestHeaderMap.get(ProtocolKey.ClientInstanceKey.SYS).toString());
            client.setIp(requestHeaderMap.get(ProtocolKey.ClientInstanceKey.IP).toString());
            client.setPid(requestHeaderMap.get(ProtocolKey.ClientInstanceKey.PID).toString());
            client.setConsumerGroup(consumerGroup);
            client.setTopic(item.getTopic());
            client.setUrl(url);
            client.setLastUpTime(new Date());

            final String groupTopicKey = client.getConsumerGroup() + "@" + client.getTopic();

            List<Client> localClients =
                    eventMeshHTTPServer.localClientInfoMapping.get(groupTopicKey);
            if (localClients == null) {
                localClients = new ArrayList<>();
                eventMeshHTTPServer.localClientInfoMapping.put(groupTopicKey, localClients);
            }

            boolean isContains = false;
            for (final Client localClient : localClients) {
                if (StringUtils.equals(localClient.getUrl(), client.getUrl())) {
                    isContains = true;
                    localClient.setLastUpTime(client.getLastUpTime());
                    break;
                }
            }

            if (!isContains) {
                localClients.add(client);
            }

        }
    }

}