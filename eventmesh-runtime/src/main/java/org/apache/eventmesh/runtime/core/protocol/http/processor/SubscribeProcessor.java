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

import org.apache.eventmesh.common.protocol.SubscriptionItem;
import org.apache.eventmesh.common.protocol.http.HttpCommand;
import org.apache.eventmesh.common.protocol.http.body.client.SubscribeRequestBody;
import org.apache.eventmesh.common.protocol.http.body.client.SubscribeResponseBody;
import org.apache.eventmesh.common.protocol.http.body.message.SendMessageResponseBody;
import org.apache.eventmesh.common.protocol.http.common.EventMeshRetCode;
import org.apache.eventmesh.common.protocol.http.common.RequestCode;
import org.apache.eventmesh.common.protocol.http.header.client.SubscribeRequestHeader;
import org.apache.eventmesh.common.protocol.http.header.client.SubscribeResponseHeader;
import org.apache.eventmesh.common.utils.IPUtils;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.runtime.acl.Acl;
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
import org.apache.eventmesh.runtime.util.WebhookUtil;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.channel.ChannelHandlerContext;

public class SubscribeProcessor implements HttpRequestProcessor {

    public Logger httpLogger = LoggerFactory.getLogger("http");

    public Logger aclLogger = LoggerFactory.getLogger("acl");

    private EventMeshHTTPServer eventMeshHTTPServer;

    public SubscribeProcessor(EventMeshHTTPServer eventMeshHTTPServer) {
        this.eventMeshHTTPServer = eventMeshHTTPServer;
    }

    @Override
    public void processRequest(ChannelHandlerContext ctx, AsyncContext<HttpCommand> asyncContext)
            throws Exception {
        HttpCommand responseEventMeshCommand;
        final HttpCommand request = asyncContext.getRequest();
        final Integer requestCode = Integer.valueOf(asyncContext.getRequest().getRequestCode());

        httpLogger.info("cmd={}|{}|client2eventMesh|from={}|to={}",
                RequestCode.get(requestCode),
                EventMeshConstants.PROTOCOL_HTTP,
                RemotingHelper.parseChannelRemoteAddr(ctx.channel()), IPUtils.getLocalAddress()
        );
        SubscribeRequestHeader subscribeRequestHeader = (SubscribeRequestHeader) request.getHeader();
        SubscribeRequestBody subscribeRequestBody = (SubscribeRequestBody) request.getBody();

        SubscribeResponseHeader subscribeResponseHeader =
                SubscribeResponseHeader
                        .buildHeader(requestCode,
                                eventMeshHTTPServer.getEventMeshHttpConfiguration().eventMeshCluster,
                                IPUtils.getLocalAddress(),
                                eventMeshHTTPServer.getEventMeshHttpConfiguration().eventMeshEnv,
                                eventMeshHTTPServer.getEventMeshHttpConfiguration().eventMeshIDC);

        //validate header
        if (StringUtils.isBlank(subscribeRequestHeader.getIdc())
                || StringUtils.isBlank(subscribeRequestHeader.getPid())
                || !StringUtils.isNumeric(subscribeRequestHeader.getPid())
                || StringUtils.isBlank(subscribeRequestHeader.getSys())) {
            responseEventMeshCommand = request.createHttpCommandResponse(
                    subscribeResponseHeader,
                    SubscribeResponseBody
                            .buildBody(EventMeshRetCode.EVENTMESH_PROTOCOL_HEADER_ERR.getRetCode(),
                                    EventMeshRetCode.EVENTMESH_PROTOCOL_HEADER_ERR.getErrMsg()));
            asyncContext.onComplete(responseEventMeshCommand);
            return;
        }

        //validate body
        if (StringUtils.isBlank(subscribeRequestBody.getUrl())
                || CollectionUtils.isEmpty(subscribeRequestBody.getTopics())
                || StringUtils.isBlank(subscribeRequestBody.getConsumerGroup())) {

            responseEventMeshCommand = request.createHttpCommandResponse(
                    subscribeResponseHeader,
                    SubscribeResponseBody
                            .buildBody(EventMeshRetCode.EVENTMESH_PROTOCOL_BODY_ERR.getRetCode(),
                                    EventMeshRetCode.EVENTMESH_PROTOCOL_BODY_ERR.getErrMsg()));
            asyncContext.onComplete(responseEventMeshCommand);
            return;
        }
        List<SubscriptionItem> subTopicList = subscribeRequestBody.getTopics();

        //do acl check
        if (eventMeshHTTPServer.getEventMeshHttpConfiguration().eventMeshServerSecurityEnable) {
            String remoteAddr = RemotingHelper.parseChannelRemoteAddr(ctx.channel());
            String user = subscribeRequestHeader.getUsername();
            String pass = subscribeRequestHeader.getPasswd();
            String subsystem = subscribeRequestHeader.getSys();
            for (SubscriptionItem item : subTopicList) {
                try {
                    Acl.doAclCheckInHttpReceive(remoteAddr, user, pass, subsystem, item.getTopic(),
                            requestCode);
                } catch (Exception e) {

                    responseEventMeshCommand = asyncContext.getRequest().createHttpCommandResponse(
                            subscribeResponseHeader,
                            SendMessageResponseBody
                                    .buildBody(EventMeshRetCode.EVENTMESH_ACL_ERR.getRetCode(),
                                            e.getMessage()));
                    asyncContext.onComplete(responseEventMeshCommand);
                    aclLogger
                            .warn("CLIENT HAS NO PERMISSION,SubscribeProcessor subscribe failed", e);
                    return;
                }
            }
        }

        String url = subscribeRequestBody.getUrl();
        String consumerGroup = subscribeRequestBody.getConsumerGroup();

        // validate URL
        try {
            if (!IPUtils.isValidDomainOrIp(url, eventMeshHTTPServer.getEventMeshHttpConfiguration().eventMeshIpv4BlackList,
                eventMeshHTTPServer.getEventMeshHttpConfiguration().eventMeshIpv6BlackList)) {
                httpLogger.error("subscriber url {} is not valid", url);
                responseEventMeshCommand = request.createHttpCommandResponse(
                    subscribeResponseHeader,
                    SubscribeResponseBody
                        .buildBody(EventMeshRetCode.EVENTMESH_PROTOCOL_BODY_ERR.getRetCode(),
                            EventMeshRetCode.EVENTMESH_PROTOCOL_BODY_ERR.getErrMsg() + " invalid URL: " + url));
                asyncContext.onComplete(responseEventMeshCommand);
                return;
            }
        } catch (Exception e) {
            httpLogger.error("subscriber url {} is not valid, error {}", url, e.getMessage());
            responseEventMeshCommand = request.createHttpCommandResponse(
                subscribeResponseHeader,
                SubscribeResponseBody
                    .buildBody(EventMeshRetCode.EVENTMESH_PROTOCOL_BODY_ERR.getRetCode(),
                        EventMeshRetCode.EVENTMESH_PROTOCOL_BODY_ERR.getErrMsg() + " invalid URL: " + url));
            asyncContext.onComplete(responseEventMeshCommand);
            return;
        }

        // obtain webhook delivery agreement for Abuse Protection
        boolean isWebhookAllowed = WebhookUtil.obtainDeliveryAgreement(eventMeshHTTPServer.httpClientPool.getClient(),
            url, eventMeshHTTPServer.getEventMeshHttpConfiguration().eventMeshWebhookOrigin);

        if (!isWebhookAllowed) {
            httpLogger.error("subscriber url {} is not allowed by the target system", url);
            responseEventMeshCommand = request.createHttpCommandResponse(
                subscribeResponseHeader,
                SubscribeResponseBody
                    .buildBody(EventMeshRetCode.EVENTMESH_PROTOCOL_BODY_ERR.getRetCode(),
                        EventMeshRetCode.EVENTMESH_PROTOCOL_BODY_ERR.getErrMsg() + " unauthorized webhook URL: " + url));
            asyncContext.onComplete(responseEventMeshCommand);
            return;
        }

        synchronized (eventMeshHTTPServer.localClientInfoMapping) {

            registerClient(subscribeRequestHeader, consumerGroup, subTopicList, url);

            for (SubscriptionItem subTopic : subTopicList) {
                List<Client> groupTopicClients = eventMeshHTTPServer.localClientInfoMapping
                        .get(consumerGroup + "@" + subTopic.getTopic());

                if (CollectionUtils.isEmpty(groupTopicClients)) {
                    httpLogger.error("group {} topic {} clients is empty", consumerGroup, subTopic);
                }

                Map<String, List<String>> idcUrls = new HashMap<>();
                for (Client client : groupTopicClients) {
                    if (idcUrls.containsKey(client.idc)) {
                        idcUrls.get(client.idc).add(StringUtils.deleteWhitespace(client.url));
                    } else {
                        List<String> urls = new ArrayList<>();
                        urls.add(client.url);
                        idcUrls.put(client.idc, urls);
                    }
                }
                ConsumerGroupConf consumerGroupConf =
                        eventMeshHTTPServer.localConsumerGroupMapping.get(consumerGroup);
                if (consumerGroupConf == null) {
                    // new subscription
                    consumerGroupConf = new ConsumerGroupConf(consumerGroup);
                    ConsumerGroupTopicConf consumeTopicConfig = new ConsumerGroupTopicConf();
                    consumeTopicConfig.setConsumerGroup(consumerGroup);
                    consumeTopicConfig.setTopic(subTopic.getTopic());
                    consumeTopicConfig.setSubscriptionItem(subTopic);
                    consumeTopicConfig.setUrls(new HashSet<>(Arrays.asList(url)));

                    consumeTopicConfig.setIdcUrls(idcUrls);

                    Map<String, ConsumerGroupTopicConf> map = new HashMap<>();
                    map.put(subTopic.getTopic(), consumeTopicConfig);
                    consumerGroupConf.setConsumerGroupTopicConf(map);
                } else {
                    // already subscribed
                    Map<String, ConsumerGroupTopicConf> map =
                            consumerGroupConf.getConsumerGroupTopicConf();
                    if (!map.containsKey(subTopic.getTopic())) {
                        //If there are multiple topics, append it
                        ConsumerGroupTopicConf newTopicConf = new ConsumerGroupTopicConf();
                        newTopicConf.setConsumerGroup(consumerGroup);
                        newTopicConf.setTopic(subTopic.getTopic());
                        newTopicConf.setSubscriptionItem(subTopic);
                        newTopicConf.setUrls(new HashSet<>(Arrays.asList(url)));
                        newTopicConf.setIdcUrls(idcUrls);
                        map.put(subTopic.getTopic(), newTopicConf);
                    }
                    for (Map.Entry<String, ConsumerGroupTopicConf> set: map.entrySet()) {
                        if (StringUtils.equals(subTopic.getTopic(), set.getKey())) {
                            ConsumerGroupTopicConf latestTopicConf = new ConsumerGroupTopicConf();
                            latestTopicConf.setConsumerGroup(consumerGroup);
                            latestTopicConf.setTopic(subTopic.getTopic());
                            latestTopicConf.setSubscriptionItem(subTopic);
                            latestTopicConf.setUrls(new HashSet<>(Arrays.asList(url)));

                            ConsumerGroupTopicConf currentTopicConf = set.getValue();
                            latestTopicConf.getUrls().addAll(currentTopicConf.getUrls());
                            latestTopicConf.setIdcUrls(idcUrls);

                            map.put(set.getKey(), latestTopicConf);
                        }
                    }
                }
                eventMeshHTTPServer.localConsumerGroupMapping.put(consumerGroup, consumerGroupConf);
            }

            long startTime = System.currentTimeMillis();
            try {
                // subscription relationship change notification
                eventMeshHTTPServer.getConsumerManager().notifyConsumerManager(consumerGroup,
                        eventMeshHTTPServer.localConsumerGroupMapping.get(consumerGroup));

                final CompleteHandler<HttpCommand> handler = new CompleteHandler<HttpCommand>() {
                    @Override
                    public void onResponse(HttpCommand httpCommand) {
                        try {
                            if (httpLogger.isDebugEnabled()) {
                                httpLogger.debug("{}", httpCommand);
                            }
                            eventMeshHTTPServer.sendResponse(ctx, httpCommand.httpResponse());
                            eventMeshHTTPServer.metrics.getSummaryMetrics().recordHTTPReqResTimeCost(
                                System.currentTimeMillis() - request.getReqTime());
                        } catch (Exception ex) {
                            // ignore
                        }
                    }
                };

                responseEventMeshCommand = request.createHttpCommandResponse(EventMeshRetCode.SUCCESS);
                asyncContext.onComplete(responseEventMeshCommand, handler);
            } catch (Exception e) {
                HttpCommand err = asyncContext.getRequest().createHttpCommandResponse(
                    subscribeResponseHeader,
                    SubscribeResponseBody
                        .buildBody(EventMeshRetCode.EVENTMESH_SUBSCRIBE_ERR.getRetCode(),
                            EventMeshRetCode.EVENTMESH_SUBSCRIBE_ERR.getErrMsg()
                                + EventMeshUtil.stackTrace(e, 2)));
                asyncContext.onComplete(err);
                long endTime = System.currentTimeMillis();
                httpLogger.error(
                    "message|eventMesh2mq|REQ|ASYNC|send2MQCost={}ms|topic={}"
                        + "|bizSeqNo={}|uniqueId={}", endTime - startTime,
                    JsonUtils.serialize(subscribeRequestBody.getTopics()),
                    subscribeRequestBody.getUrl(), e);
                eventMeshHTTPServer.metrics.getSummaryMetrics().recordSendMsgFailed();
                eventMeshHTTPServer.metrics.getSummaryMetrics().recordSendMsgCost(endTime - startTime);
            }
        }
    }

    @Override
    public boolean rejectRequest() {
        return false;
    }

    private void registerClient(SubscribeRequestHeader subscribeRequestHeader, String consumerGroup,
                                List<SubscriptionItem> subscriptionItems, String url) {
        for (SubscriptionItem item : subscriptionItems) {
            Client client = new Client();
            client.env = subscribeRequestHeader.getEnv();
            client.idc = subscribeRequestHeader.getIdc();
            client.sys = subscribeRequestHeader.getSys();
            client.ip = subscribeRequestHeader.getIp();
            client.pid = subscribeRequestHeader.getPid();
            client.consumerGroup = consumerGroup;
            client.topic = item.getTopic();
            client.url = url;
            client.lastUpTime = new Date();

            String groupTopicKey = client.consumerGroup + "@" + client.topic;

            if (eventMeshHTTPServer.localClientInfoMapping.containsKey(groupTopicKey)) {
                List<Client> localClients =
                        eventMeshHTTPServer.localClientInfoMapping.get(groupTopicKey);
                boolean isContains = false;
                for (Client localClient : localClients) {
                    if (StringUtils.equals(localClient.url, client.url)) {
                        isContains = true;
                        localClient.lastUpTime = client.lastUpTime;
                        break;
                    }
                }
                if (!isContains) {
                    localClients.add(client);
                }
            } else {
                List<Client> clients = new ArrayList<>();
                clients.add(client);
                eventMeshHTTPServer.localClientInfoMapping.put(groupTopicKey, clients);
            }
        }
    }

}
