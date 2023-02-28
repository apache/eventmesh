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

import org.apache.eventmesh.common.protocol.http.HttpCommand;
import org.apache.eventmesh.common.protocol.http.body.client.UnSubscribeRequestBody;
import org.apache.eventmesh.common.protocol.http.body.client.UnSubscribeResponseBody;
import org.apache.eventmesh.common.protocol.http.common.EventMeshRetCode;
import org.apache.eventmesh.common.protocol.http.common.RequestCode;
import org.apache.eventmesh.common.protocol.http.header.client.UnSubscribeRequestHeader;
import org.apache.eventmesh.common.protocol.http.header.client.UnSubscribeResponseHeader;
import org.apache.eventmesh.common.utils.IPUtils;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.runtime.boot.EventMeshHTTPServer;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.consumergroup.ConsumerGroupConf;
import org.apache.eventmesh.runtime.core.consumergroup.ConsumerGroupTopicConf;
import org.apache.eventmesh.runtime.core.protocol.http.async.AsyncContext;
import org.apache.eventmesh.runtime.core.protocol.http.async.CompleteHandler;
import org.apache.eventmesh.runtime.core.protocol.http.processor.inf.Client;
import org.apache.eventmesh.runtime.core.protocol.http.processor.inf.HttpRequestProcessor;
import org.apache.eventmesh.runtime.util.EventMeshUtil;
import org.apache.eventmesh.runtime.util.RemotingHelper;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.channel.ChannelHandlerContext;

public class UnSubscribeProcessor implements HttpRequestProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(UnSubscribeProcessor.class);

    private final transient EventMeshHTTPServer eventMeshHTTPServer;

    public UnSubscribeProcessor(final EventMeshHTTPServer eventMeshHTTPServer) {
        this.eventMeshHTTPServer = eventMeshHTTPServer;
    }

    @Override
    public void processRequest(final ChannelHandlerContext ctx, final AsyncContext<HttpCommand> asyncContext)
            throws Exception {
        HttpCommand responseEventMeshCommand;
        final String localAddress = IPUtils.getLocalAddress();
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("cmd={}|{}|client2eventMesh|from={}|to={}",
                    RequestCode.get(Integer.valueOf(asyncContext.getRequest().getRequestCode())),
                    EventMeshConstants.PROTOCOL_HTTP,
                    RemotingHelper.parseChannelRemoteAddr(ctx.channel()), localAddress);
        }

        final UnSubscribeRequestHeader unSubscribeRequestHeader =
                (UnSubscribeRequestHeader) asyncContext.getRequest().getHeader();
        final UnSubscribeRequestBody unSubscribeRequestBody =
                (UnSubscribeRequestBody) asyncContext.getRequest().getBody();
        final UnSubscribeResponseHeader unSubscribeResponseHeader =
                UnSubscribeResponseHeader
                        .buildHeader(Integer.valueOf(asyncContext.getRequest().getRequestCode()),
                                eventMeshHTTPServer.getEventMeshHttpConfiguration().getEventMeshCluster(),
                                localAddress, eventMeshHTTPServer.getEventMeshHttpConfiguration().getEventMeshEnv(),
                                eventMeshHTTPServer.getEventMeshHttpConfiguration().getEventMeshIDC());

        //validate header
        if (StringUtils.isBlank(unSubscribeRequestHeader.getIdc())
                || StringUtils.isBlank(unSubscribeRequestHeader.getPid())
                || !StringUtils.isNumeric(unSubscribeRequestHeader.getPid())
                || StringUtils.isBlank(unSubscribeRequestHeader.getSys())) {
            responseEventMeshCommand = asyncContext.getRequest().createHttpCommandResponse(
                    unSubscribeResponseHeader,
                    UnSubscribeResponseBody
                            .buildBody(EventMeshRetCode.EVENTMESH_PROTOCOL_HEADER_ERR.getRetCode(),
                                    EventMeshRetCode.EVENTMESH_PROTOCOL_HEADER_ERR.getErrMsg()));
            asyncContext.onComplete(responseEventMeshCommand);
            return;
        }

        //validate body
        if (StringUtils.isBlank(unSubscribeRequestBody.getUrl())
                || CollectionUtils.isEmpty(unSubscribeRequestBody.getTopics())
                || StringUtils.isBlank(unSubscribeRequestBody.getConsumerGroup())) {

            responseEventMeshCommand = asyncContext.getRequest().createHttpCommandResponse(
                    unSubscribeResponseHeader,
                    UnSubscribeResponseBody
                            .buildBody(EventMeshRetCode.EVENTMESH_PROTOCOL_BODY_ERR.getRetCode(),
                                    EventMeshRetCode.EVENTMESH_PROTOCOL_BODY_ERR.getErrMsg()));
            asyncContext.onComplete(responseEventMeshCommand);
            return;
        }

        final String pid = unSubscribeRequestHeader.getPid();
        final String consumerGroup = unSubscribeRequestBody.getConsumerGroup();
        final String unSubscribeUrl = unSubscribeRequestBody.getUrl();
        final List<String> unSubTopicList = unSubscribeRequestBody.getTopics();

        final CompleteHandler<HttpCommand> handler = new CompleteHandler<HttpCommand>() {
            @Override
            public void onResponse(final HttpCommand httpCommand) {
                try {
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("{}", httpCommand);
                    }
                    eventMeshHTTPServer.sendResponse(ctx, httpCommand.httpResponse());
                    eventMeshHTTPServer.getMetrics().getSummaryMetrics().recordHTTPReqResTimeCost(
                            System.currentTimeMillis() - asyncContext.getRequest().getReqTime());
                } catch (Exception ex) {
                    // ignore
                }
            }
        };

        synchronized (eventMeshHTTPServer.getSubscriptionManager().getLocalClientInfoMapping()) {
            boolean isChange = true;

            registerClient(unSubscribeRequestHeader, consumerGroup, unSubTopicList, unSubscribeUrl);

            for (final String unSubTopic : unSubTopicList) {
                final List<Client> groupTopicClients = eventMeshHTTPServer.getSubscriptionManager().getLocalClientInfoMapping()
                        .get(consumerGroup + "@" + unSubTopic);

                final Iterator<Client> clientIterator = groupTopicClients.iterator();
                while (clientIterator.hasNext()) {
                    final Client client = clientIterator.next();
                    if (StringUtils.equals(client.getPid(), pid)
                            && StringUtils.equals(client.getUrl(), unSubscribeUrl)) {
                        if (LOGGER.isWarnEnabled()) {
                            LOGGER.warn("client {} start unsubscribe", JsonUtils.serialize(client));
                        }
                        clientIterator.remove();
                    }
                }
                if (CollectionUtils.isNotEmpty(groupTopicClients)) {
                    //change url
                    final Map<String, List<String>> idcUrls = new HashMap<>();
                    final Set<String> clientUrls = new HashSet<>();
                    for (final Client client : groupTopicClients) {
                        // remove subscribed url
                        if (!StringUtils.equals(unSubscribeUrl, client.getUrl())) {
                            clientUrls.add(client.getUrl());

                            List<String> urls = idcUrls.get(client.getIdc());
                            if (urls == null) {
                                urls = new ArrayList<String>();
                                idcUrls.put(client.getIdc(), urls);
                            }
                        }

                    }
                    synchronized (eventMeshHTTPServer.getSubscriptionManager().getLocalConsumerGroupMapping()) {
                        final ConsumerGroupConf consumerGroupConf =
                                eventMeshHTTPServer.getSubscriptionManager().getLocalConsumerGroupMapping().get(consumerGroup);

                        final Map<String, ConsumerGroupTopicConf> map =
                                consumerGroupConf.getConsumerGroupTopicConf();
                        for (final Map.Entry<String, ConsumerGroupTopicConf> topicConf : map.entrySet()) {
                            // only modify the topic to subscribe
                            if (StringUtils.equals(unSubTopic, topicConf.getKey())) {
                                final ConsumerGroupTopicConf latestTopicConf =
                                        new ConsumerGroupTopicConf();
                                latestTopicConf.setConsumerGroup(consumerGroup);
                                latestTopicConf.setTopic(unSubTopic);
                                latestTopicConf
                                        .setSubscriptionItem(topicConf.getValue().getSubscriptionItem());
                                latestTopicConf.setUrls(clientUrls);

                                latestTopicConf.setIdcUrls(idcUrls);

                                map.put(unSubTopic, latestTopicConf);
                            }
                        }
                        eventMeshHTTPServer.getSubscriptionManager().getLocalConsumerGroupMapping()
                                .put(consumerGroup, consumerGroupConf);
                    }
                } else {
                    isChange = false;
                    break;
                }
            }

            final long startTime = System.currentTimeMillis();
            if (isChange) {
                try {
                    eventMeshHTTPServer.getConsumerManager().notifyConsumerManager(consumerGroup,
                            eventMeshHTTPServer.getSubscriptionManager().getLocalConsumerGroupMapping().get(consumerGroup));

                    responseEventMeshCommand =
                            asyncContext.getRequest().createHttpCommandResponse(EventMeshRetCode.SUCCESS);
                    asyncContext.onComplete(responseEventMeshCommand, handler);

                } catch (Exception e) {
                    final HttpCommand err = asyncContext.getRequest().createHttpCommandResponse(
                            unSubscribeResponseHeader,
                            UnSubscribeResponseBody
                                    .buildBody(EventMeshRetCode.EVENTMESH_UNSUBSCRIBE_ERR.getRetCode(),
                                            EventMeshRetCode.EVENTMESH_UNSUBSCRIBE_ERR.getErrMsg()
                                                    + EventMeshUtil.stackTrace(e, 2)));
                    asyncContext.onComplete(err);
                    final long endTime = System.currentTimeMillis();
                    LOGGER.error("message|eventMesh2mq|REQ|ASYNC|send2MQCost={}ms|topic={}|url={}",
                            endTime - startTime,
                            JsonUtils.serialize(unSubscribeRequestBody.getTopics()),
                            unSubscribeRequestBody.getUrl(), e);
                    eventMeshHTTPServer.getMetrics().getSummaryMetrics().recordSendMsgFailed();
                    eventMeshHTTPServer.getMetrics().getSummaryMetrics().recordSendMsgCost(endTime - startTime);
                }
            } else {
                //remove
                try {
                    eventMeshHTTPServer.getConsumerManager()
                            .notifyConsumerManager(consumerGroup, null);
                    responseEventMeshCommand =
                            asyncContext.getRequest().createHttpCommandResponse(EventMeshRetCode.SUCCESS);
                    asyncContext.onComplete(responseEventMeshCommand, handler);
                    // clean ClientInfo
                    eventMeshHTTPServer.getSubscriptionManager().getLocalClientInfoMapping().keySet()
                            .removeIf(s -> StringUtils.contains(s, consumerGroup));
                    // clean ConsumerGroupInfo
                    eventMeshHTTPServer.getSubscriptionManager().getLocalConsumerGroupMapping().keySet()
                            .removeIf(s -> StringUtils.equals(consumerGroup, s));
                } catch (Exception e) {
                    final HttpCommand err = asyncContext.getRequest().createHttpCommandResponse(
                            unSubscribeResponseHeader,
                            UnSubscribeResponseBody
                                    .buildBody(EventMeshRetCode.EVENTMESH_UNSUBSCRIBE_ERR.getRetCode(),
                                            EventMeshRetCode.EVENTMESH_UNSUBSCRIBE_ERR.getErrMsg()
                                                    + EventMeshUtil.stackTrace(e, 2)));
                    asyncContext.onComplete(err);
                    final long endTime = System.currentTimeMillis();
                    LOGGER.error("message|eventMesh2mq|REQ|ASYNC|send2MQCost={}ms"
                                    + "|topic={}|url={}", endTime - startTime,
                            JsonUtils.serialize(unSubscribeRequestBody.getTopics()),
                            unSubscribeRequestBody.getUrl(), e);
                    eventMeshHTTPServer.getMetrics().getSummaryMetrics().recordSendMsgFailed();
                    eventMeshHTTPServer.getMetrics().getSummaryMetrics().recordSendMsgCost(endTime - startTime);
                }
            }
        }
    }

    @Override
    public boolean rejectRequest() {
        return false;
    }

    private void registerClient(final UnSubscribeRequestHeader unSubscribeRequestHeader,
                                final String consumerGroup,
                                final List<String> topicList,
                                final String url) {
        for (final String topic : topicList) {
            final Client client = new Client();
            client.setEnv(unSubscribeRequestHeader.getEnv());
            client.setIdc(unSubscribeRequestHeader.getIdc());
            client.setSys(unSubscribeRequestHeader.getSys());
            client.setIp(unSubscribeRequestHeader.getIp());
            client.setPid(unSubscribeRequestHeader.getPid());
            client.setConsumerGroup(consumerGroup);
            client.setTopic(topic);
            client.setUrl(url);
            client.setLastUpTime(new Date());

            final String groupTopicKey = client.getConsumerGroup() + "@" + client.getTopic();
            if (eventMeshHTTPServer.getSubscriptionManager().getLocalClientInfoMapping().containsKey(groupTopicKey)) {
                final List<Client> localClients =
                        eventMeshHTTPServer.getSubscriptionManager().getLocalClientInfoMapping().get(groupTopicKey);
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
            } else {
                final List<Client> clients = new ArrayList<>();
                clients.add(client);
                eventMeshHTTPServer.getSubscriptionManager().getLocalClientInfoMapping().put(groupTopicKey, clients);
            }
        }
    }
}
