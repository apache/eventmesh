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

import static org.apache.eventmesh.runtime.core.protocol.http.push.AsyncHTTPPushRequest.LOGGER;

import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.protocol.SubscriptionItem;
import org.apache.eventmesh.common.protocol.http.HttpEventWrapper;
import org.apache.eventmesh.common.protocol.http.common.EventMeshRetCode;
import org.apache.eventmesh.common.protocol.http.common.ProtocolKey;
import org.apache.eventmesh.common.protocol.http.common.RequestURI;
import org.apache.eventmesh.common.utils.IPUtils;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.runtime.boot.EventMeshHTTPServer;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.consumer.ClientInfo;
import org.apache.eventmesh.runtime.core.consumer.SubscriptionManager;
import org.apache.eventmesh.runtime.core.protocol.http.processor.inf.AbstractEventProcessor;
import org.apache.eventmesh.runtime.util.RemotingHelper;
import org.apache.eventmesh.runtime.util.WebhookUtil;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.incubator.codec.quic.InsecureQuicTokenHandler;
import io.netty.incubator.codec.quic.QuicChannel;
import io.netty.incubator.codec.quic.QuicServerCodecBuilder;
import io.netty.incubator.codec.quic.QuicSslContext;
import io.netty.incubator.codec.quic.QuicSslContextBuilder;

import com.fasterxml.jackson.core.type.TypeReference;

public class QuicLocalSubscribeEventProcessor extends AbstractEventProcessor {
    public QuicLocalSubscribeEventProcessor(EventMeshHTTPServer eventMeshHTTPServer) {
        super(eventMeshHTTPServer);
    }

    @Override
    public void handler(HandlerService.HandlerSpecific handlerSpecific, HttpRequest httpRequest) throws Exception {
        SelfSignedCertificate selfSignedCertificate = new SelfSignedCertificate();
        QuicSslContext context = QuicSslContextBuilder.forServer(
                selfSignedCertificate.privateKey(), null, selfSignedCertificate.certificate()).build();
        NioEventLoopGroup group = new NioEventLoopGroup(1);
        ChannelHandler codec = new QuicServerCodecBuilder().sslContext(context)
            .maxIdleTimeout(5000, TimeUnit.MILLISECONDS)
            // Configure some limits for the maximal number of streams (and the data) that we want to handle.
            .initialMaxData(10000000)
            .initialMaxStreamDataBidirectionalLocal(1000000)
            .initialMaxStreamDataBidirectionalRemote(1000000)
            .initialMaxStreamsBidirectional(100)
            .initialMaxStreamsUnidirectional(100)

            // Setup a token handler.
            .tokenHandler(InsecureQuicTokenHandler.INSTANCE)
            // ChannelHandler that is added into QuicChannel pipeline.
            .handler(new ChannelInboundHandlerAdapter() {
                @Override
                public void channelActive(ChannelHandlerContext ctx) {
                    QuicChannel channel = (QuicChannel) ctx.channel();
                    String localAddress = IPUtils.getLocalAddress();
                    String remoteAddr = RemotingHelper.parseChannelRemoteAddr(channel);
                    final HttpEventWrapper requestWrapper = handlerSpecific.getAsyncContext().getRequest();
                    // user request header
                    requestWrapper.getHeaderMap().put(ProtocolKey.ClientInstanceKey.IP.getKey(), remoteAddr);
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
                    final Map<String, Object> requestBodyMap = Optional.ofNullable(JsonUtils.parseTypeReferenceObject(
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
                    final String topic = JsonUtils.toJSONString(requestBodyMap.get("topic"));

                    // SubscriptionItem
                    final List<SubscriptionItem> subscriptionList = Optional.ofNullable(JsonUtils.parseTypeReferenceObject(
                        topic,
                        new TypeReference<List<SubscriptionItem>>() {
                        }
                    )).orElseGet(Collections::emptyList);
                    // validate URL
                    try {
                        if (!IPUtils.isValidDomainOrIp(url, eventMeshHTTPServer.getEventMeshHttpConfiguration().getEventMeshIpv4BlackList(),
                            eventMeshHTTPServer.getEventMeshHttpConfiguration().getEventMeshIpv6BlackList())) {
                            handlerSpecific.sendErrorResponse(EventMeshRetCode.EVENTMESH_PROTOCOL_BODY_ERR, responseHeaderMap,
                                responseBodyMap, null);
                            return;
                        }
                    } catch (Exception e) {
                        handlerSpecific.sendErrorResponse(EventMeshRetCode.EVENTMESH_PROTOCOL_BODY_ERR, responseHeaderMap,
                            responseBodyMap, null);
                        return;
                    }

                    // obtain webhook delivery agreement for Abuse Protection
                    if (!WebhookUtil.obtainDeliveryAgreement(eventMeshHTTPServer.getHttpClientPool().getClient(),
                        url, eventMeshHTTPServer.getEventMeshHttpConfiguration().getEventMeshWebhookOrigin())) {
                        handlerSpecific.sendErrorResponse(EventMeshRetCode.EVENTMESH_PROTOCOL_BODY_ERR, responseHeaderMap,
                            responseBodyMap, null);
                        return;
                    }

                    synchronized (eventMeshHTTPServer.getSubscriptionManager().getLocalClientInfoMapping()) {
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
                            handlerSpecific.sendErrorResponse(EventMeshRetCode.EVENTMESH_SUBSCRIBE_ERR, responseHeaderMap,
                                responseBodyMap, null);
                        }

                        // Update service metadata
                        updateMetadata();
                    }
                }

                public void channelInactive(ChannelHandlerContext ctx) {
                    ((QuicChannel) ctx.channel()).collectStats().addListener(f -> {
                        if (f.isSuccess()) {
                            LOGGER.info("Connection closed: {}", f.getNow());
                        }
                    });
                }

                @Override
                public boolean isSharable() {
                    return true;
                }
            }).build();
        try {
            Bootstrap bs = new Bootstrap();
            Channel channel = bs.group(group)
                .channel(NioDatagramChannel.class)
                .handler(codec)
                .bind(new InetSocketAddress(9999)).sync().channel();
            channel.closeFuture().sync();
        } finally {
            group.shutdownGracefully();
        }
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

    @Override
    public String[] paths() {
        return new String[]{RequestURI.QUIC_SUBSCRIBE_LOCAL.getRequestURI()};
    }
}
