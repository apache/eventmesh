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

package com.webank.eventmesh.runtime.core.protocol.http.processor;

import com.webank.eventmesh.common.protocol.http.body.client.SubscribeRequestBody;
import com.webank.eventmesh.common.protocol.http.body.client.SubscribeResponseBody;
import com.webank.eventmesh.common.protocol.http.body.message.SendMessageRequestBody;
import com.webank.eventmesh.common.protocol.http.body.message.SendMessageResponseBody;
import com.webank.eventmesh.common.protocol.http.common.ProxyRetCode;
import com.webank.eventmesh.common.protocol.http.header.client.SubscribeRequestHeader;
import com.webank.eventmesh.common.protocol.http.header.client.SubscribeResponseHeader;
import com.webank.eventmesh.common.protocol.http.header.message.SendMessageRequestHeader;
import com.webank.eventmesh.common.protocol.http.header.message.SendMessageResponseHeader;
import com.webank.eventmesh.runtime.core.consumergroup.ConsumerGroupConf;
import com.webank.eventmesh.runtime.core.consumergroup.ConsumerGroupTopicConf;
import com.webank.eventmesh.runtime.core.consumergroup.event.ConsumerGroupStateEvent;
import com.webank.eventmesh.runtime.core.protocol.http.async.CompleteHandler;
import com.webank.eventmesh.runtime.core.protocol.http.consumer.ConsumerGroupManager;
import com.webank.eventmesh.runtime.core.protocol.http.consumer.ProxyConsumer;
import com.webank.eventmesh.runtime.core.protocol.http.processor.inf.HttpRequestProcessor;
import com.webank.eventmesh.runtime.boot.ProxyHTTPServer;
import com.webank.eventmesh.runtime.constants.ProxyConstants;
import com.webank.eventmesh.runtime.core.protocol.http.async.AsyncContext;
import com.webank.eventmesh.common.IPUtil;
import com.webank.eventmesh.common.command.HttpCommand;
import com.webank.eventmesh.common.protocol.http.common.RequestCode;
import com.webank.eventmesh.runtime.core.protocol.http.producer.ProxyProducer;
import com.webank.eventmesh.runtime.util.ProxyUtil;
import com.webank.eventmesh.runtime.util.RemotingHelper;
import io.netty.channel.ChannelHandlerContext;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SubscribeProcessor implements HttpRequestProcessor {

    public Logger cmdLogger = LoggerFactory.getLogger("cmd");

    public Logger httpLogger = LoggerFactory.getLogger("http");

    private ProxyHTTPServer proxyHTTPServer;

    public SubscribeProcessor(ProxyHTTPServer proxyHTTPServer) {
        this.proxyHTTPServer = proxyHTTPServer;
    }

    @Override
    public void processRequest(ChannelHandlerContext ctx, AsyncContext<HttpCommand> asyncContext) throws Exception {
        HttpCommand responseProxyCommand;
        cmdLogger.info("cmd={}|{}|client2proxy|from={}|to={}", RequestCode.get(Integer.valueOf(asyncContext.getRequest().getRequestCode())),
                ProxyConstants.PROTOCOL_HTTP,
                RemotingHelper.parseChannelRemoteAddr(ctx.channel()), IPUtil.getLocalAddress());
        SubscribeRequestHeader subscribeRequestHeader = (SubscribeRequestHeader) asyncContext.getRequest().getHeader();
        SubscribeRequestBody subscribeRequestBody = (SubscribeRequestBody) asyncContext.getRequest().getBody();

        SubscribeResponseHeader subscribeResponseHeader =
                SubscribeResponseHeader.buildHeader(Integer.valueOf(asyncContext.getRequest().getRequestCode()), proxyHTTPServer.getProxyConfiguration().proxyCluster,
                        IPUtil.getLocalAddress(), proxyHTTPServer.getProxyConfiguration().proxyEnv,
                        proxyHTTPServer.getProxyConfiguration().proxyRegion,
                        proxyHTTPServer.getProxyConfiguration().proxyDCN, proxyHTTPServer.getProxyConfiguration().proxyIDC);


        //validate header
        if (StringUtils.isBlank(subscribeRequestHeader.getIdc())
                || StringUtils.isBlank(subscribeRequestHeader.getDcn())
                || StringUtils.isBlank(subscribeRequestHeader.getPid())
                || !StringUtils.isNumeric(subscribeRequestHeader.getPid())
                || StringUtils.isBlank(subscribeRequestHeader.getSys())) {
            responseProxyCommand = asyncContext.getRequest().createHttpCommandResponse(
                    subscribeResponseHeader,
                    SubscribeResponseBody.buildBody(ProxyRetCode.PROXY_PROTOCOL_HEADER_ERR.getRetCode(), ProxyRetCode.PROXY_PROTOCOL_HEADER_ERR.getErrMsg()));
            asyncContext.onComplete(responseProxyCommand);
            return;
        }

        //validate body
        if (StringUtils.isBlank(subscribeRequestBody.getUrl())
                || StringUtils.isBlank(subscribeRequestBody.getTopic())) {

            responseProxyCommand = asyncContext.getRequest().createHttpCommandResponse(
                    subscribeResponseHeader,
                    SubscribeResponseBody.buildBody(ProxyRetCode.PROXY_PROTOCOL_BODY_ERR.getRetCode(), ProxyRetCode.PROXY_PROTOCOL_BODY_ERR.getErrMsg()));
            asyncContext.onComplete(responseProxyCommand);
            return;
        }
        String topic = subscribeRequestBody.getTopic();
        String url = subscribeRequestBody.getUrl();
        String consumerGroup = ProxyUtil.buildClientGroup(subscribeRequestHeader.getSys(),
                subscribeRequestHeader.getDcn());
        ConsumerGroupConf consumerGroupConf = proxyHTTPServer.localConsumerGroupMapping.get(consumerGroup);
        if (consumerGroupConf == null) {
            // 新订阅
            consumerGroupConf = new ConsumerGroupConf(consumerGroup);
            ConsumerGroupTopicConf consumeTopicConfig = new ConsumerGroupTopicConf();
            consumeTopicConfig.setConsumerGroup(consumerGroup);
            consumeTopicConfig.setTopic(topic);
            consumeTopicConfig.setUrls(new HashSet<>(Arrays.asList(url)));

            //按IDC整理url
            String clientIdc = subscribeRequestHeader.getIdc();
            Map<String, List<String>> idcUrls = new HashMap<>();
            idcUrls.put(clientIdc, Arrays.asList(url));
            consumeTopicConfig.setIdcUrls(idcUrls);

            Map<String, ConsumerGroupTopicConf> map = new HashMap<>();
            map.put(topic, consumeTopicConfig);
            consumerGroupConf.setConsumerGroupTopicConf(map);
        } else {
            // 已有订阅
            Map<String, ConsumerGroupTopicConf> map = consumerGroupConf.getConsumerGroupTopicConf();
            for (String key : map.keySet()) {
                if (StringUtils.equals(topic, key)) {
                    ConsumerGroupTopicConf latestTopicConf = new ConsumerGroupTopicConf();
                    ConsumerGroupTopicConf currentTopicConf = map.get(key);
                    latestTopicConf.setConsumerGroup(consumerGroup);
                    latestTopicConf.setTopic(topic);
                    latestTopicConf.setUrls(new HashSet<>(Arrays.asList(url)));
                    latestTopicConf.getUrls().addAll(currentTopicConf.getUrls());

                    //按IDC整理url
                    String clientIdc = subscribeRequestHeader.getIdc();
                    Map<String, List<String>> currentIdcUrls = currentTopicConf.getIdcUrls();
                    if (currentIdcUrls.containsKey(clientIdc)) {
                        currentIdcUrls.get(clientIdc).addAll(Arrays.asList(url));
                    } else {
                        currentIdcUrls.put(clientIdc, Arrays.asList(url));
                    }

                    Map<String, List<String>> idcUrls = new HashMap<>(currentIdcUrls);
                    latestTopicConf.setIdcUrls(idcUrls);

                    map.put(key, latestTopicConf);
                }
            }
        }
        proxyHTTPServer.localConsumerGroupMapping.put(consumerGroup, consumerGroupConf);

        long startTime = System.currentTimeMillis();
        try {
            // 订阅关系变化通知
            notifyConsumerManager(consumerGroup, consumerGroupConf, proxyHTTPServer.localConsumerGroupMapping);

            final CompleteHandler<HttpCommand> handler = new CompleteHandler<HttpCommand>() {
                @Override
                public void onResponse(HttpCommand httpCommand) {
                    try {
                        if (httpLogger.isDebugEnabled()) {
                            httpLogger.debug("{}", httpCommand);
                        }
                        proxyHTTPServer.sendResponse(ctx, httpCommand.httpResponse());
                        proxyHTTPServer.metrics.summaryMetrics.recordHTTPReqResTimeCost(System.currentTimeMillis() - asyncContext.getRequest().getReqTime());
                    } catch (Exception ex) {
                    }
                }
            };

            responseProxyCommand = asyncContext.getRequest().createHttpCommandResponse(
                    ProxyRetCode.SUCCESS.getRetCode(), ProxyRetCode.SUCCESS.getErrMsg());
            asyncContext.onComplete(responseProxyCommand, handler);
        } catch (Exception e) {
            HttpCommand err = asyncContext.getRequest().createHttpCommandResponse(
                    subscribeResponseHeader,
                    SubscribeResponseBody.buildBody(ProxyRetCode.PROXY_SEND_ASYNC_MSG_ERR.getRetCode(),
                            ProxyRetCode.PROXY_SEND_ASYNC_MSG_ERR.getErrMsg() + ProxyUtil.stackTrace(e, 2)));
            asyncContext.onComplete(err);
            long endTime = System.currentTimeMillis();
            httpLogger.error("message|proxy2mq|REQ|ASYNC|send2MQCost={}ms|topic={}|bizSeqNo={}|uniqueId={}",
                    endTime - startTime,
                    subscribeRequestBody.getTopic(),
                    subscribeRequestBody.getUrl(), e);
            proxyHTTPServer.metrics.summaryMetrics.recordSendMsgFailed();
            proxyHTTPServer.metrics.summaryMetrics.recordSendMsgCost(endTime - startTime);
        }

    }

    @Override
    public boolean rejectRequest() {
        return false;
    }

    /**
     * notify ConsumerManager 组级别
     */
    private void notifyConsumerManager(String consumerGroup, ConsumerGroupConf latestConsumerGroupConfig,
                                       ConcurrentHashMap<String, ConsumerGroupConf> localConsumerGroupMapping) throws Exception {
        ConsumerGroupManager cgm = proxyHTTPServer.getConsumerManager().getConsumer(consumerGroup);
        if (cgm == null) {
            ConsumerGroupStateEvent notification = new ConsumerGroupStateEvent();
            notification.action = ConsumerGroupStateEvent.ConsumerGroupStateAction.NEW;
            notification.consumerGroup = consumerGroup;
            notification.consumerGroupConfig = latestConsumerGroupConfig;
            proxyHTTPServer.getEventBus().post(notification);
            return;
        }

        if (!latestConsumerGroupConfig.equals(cgm.getConsumerGroupConfig())) {
            ConsumerGroupStateEvent notification = new ConsumerGroupStateEvent();
            notification.action = ConsumerGroupStateEvent.ConsumerGroupStateAction.CHANGE;
            notification.consumerGroup = consumerGroup;
            notification.consumerGroupConfig = latestConsumerGroupConfig;
            proxyHTTPServer.getEventBus().post(notification);
            return;
        }

//        for (String curr : weMQProxyServer.getConsumerManager().getCurrRunningGroupSet()) {
//            if (!localConsumerGroupMapping.containsKey(curr)) {
//                ConsumerGroupStateEvent notification = new ConsumerManager.ConsumerGroupStateEvent();
//                notification.action = ConsumerGroupStateAction.DELETE;
//                notification.consumerGroup = consumerGroup;
//                weMQProxyServer.getEventBus().post(notification);
//            }
//        }

        return;
    }
}
