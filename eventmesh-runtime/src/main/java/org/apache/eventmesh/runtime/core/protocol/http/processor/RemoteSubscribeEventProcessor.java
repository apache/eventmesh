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
import org.apache.eventmesh.runtime.configuration.EventMeshHTTPConfiguration;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.protocol.http.async.AsyncContext;
import org.apache.eventmesh.runtime.core.protocol.http.processor.inf.AbstractEventProcessor;
import org.apache.eventmesh.runtime.util.RemotingHelper;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.incubator.codec.quic.QuicChannel;
import io.netty.incubator.codec.quic.QuicClientCodecBuilder;
import io.netty.incubator.codec.quic.QuicSslContext;
import io.netty.incubator.codec.quic.QuicSslContextBuilder;
import io.netty.util.CharsetUtil;
import io.netty.util.NetUtil;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.Maps;

@EventMeshTrace
public class RemoteSubscribeEventProcessor extends AbstractEventProcessor {

    private static final Logger httpLogger = LoggerFactory.getLogger(EventMeshConstants.PROTOCOL_HTTP);

    private static final Logger aclLogger = LoggerFactory.getLogger(EventMeshConstants.ACL);

    private final Acl acl;

    public RemoteSubscribeEventProcessor(EventMeshHTTPServer eventMeshHTTPServer) {
        super(eventMeshHTTPServer);
        this.acl = eventMeshHTTPServer.getAcl();
    }

    @Override
    public void handler(HandlerService.HandlerSpecific handlerSpecific, HttpRequest httpRequest) throws Exception {

        AsyncContext<HttpEventWrapper> asyncContext = handlerSpecific.getAsyncContext();

        ChannelHandlerContext ctx = handlerSpecific.getCtx();

        HttpEventWrapper requestWrapper = asyncContext.getRequest();
        String localAddress = IPUtils.getLocalAddress();
        String remoteAddr = RemotingHelper.parseChannelRemoteAddr(ctx.channel());
        httpLogger.info("uri={}|{}|client2eventMesh|from={}|to={}", requestWrapper.getRequestURI(),
            EventMeshConstants.PROTOCOL_HTTP, remoteAddr, localAddress);

        // user request header
        Map<String, Object> userRequestHeaderMap = requestWrapper.getHeaderMap();
        userRequestHeaderMap.put(ProtocolKey.ClientInstanceKey.IP.getKey(), remoteAddr);

        // build sys header
        requestWrapper.buildSysHeaderForClient();

        Map<String, Object> responseHeaderMap = builderResponseHeaderMap(requestWrapper);

        Map<String, Object> sysHeaderMap = requestWrapper.getSysHeaderMap();

        Map<String, Object> responseBodyMap = new HashMap<>();

        // validate header
        if (validateSysHeader(sysHeaderMap)) {
            handlerSpecific.sendErrorResponse(EventMeshRetCode.EVENTMESH_PROTOCOL_HEADER_ERR, responseHeaderMap,
                responseBodyMap, null);
            return;
        }

        // validate body
        byte[] requestBody = requestWrapper.getBody();

        Map<String, Object> requestBodyMap = Optional.ofNullable(JsonUtils.parseTypeReferenceObject(
            new String(requestBody, Constants.DEFAULT_CHARSET),
            new TypeReference<HashMap<String, Object>>() {
            })).orElseGet(Maps::newHashMap);

        if (validatedRequestBodyMap(requestBodyMap)) {
            handlerSpecific.sendErrorResponse(EventMeshRetCode.EVENTMESH_PROTOCOL_BODY_ERR, responseHeaderMap,
                responseBodyMap, null);
            return;
        }

        // String url = requestBodyMap.get(EventMeshConstants.URL).toString();
        String topic = JsonUtils.toJSONString(requestBodyMap.get(EventMeshConstants.MANAGE_TOPIC));

        // SubscriptionItem
        List<SubscriptionItem> subscriptionList = Optional.ofNullable(JsonUtils.parseTypeReferenceObject(
            topic,
            new TypeReference<List<SubscriptionItem>>() {
            })).orElseGet(Collections::emptyList);

        // do acl check
        EventMeshHTTPConfiguration eventMeshHttpConfiguration = eventMeshHTTPServer.getEventMeshHttpConfiguration();
        if (eventMeshHttpConfiguration.isEventMeshServerSecurityEnable()) {
            String user = sysHeaderMap.get(ProtocolKey.ClientInstanceKey.USERNAME.getKey()).toString();
            String pass = sysHeaderMap.get(ProtocolKey.ClientInstanceKey.PASSWD.getKey()).toString();
            String subsystem = sysHeaderMap.get(ProtocolKey.ClientInstanceKey.SYS.getKey()).toString();
            for (SubscriptionItem item : subscriptionList) {
                try {
                    this.acl.doAclCheckInHttpReceive(remoteAddr, user, pass, subsystem, item.getTopic(), requestWrapper.getRequestURI());
                } catch (Exception e) {
                    aclLogger.warn("CLIENT HAS NO PERMISSION,SubscribeProcessor subscribe failed", e);
                    handlerSpecific.sendErrorResponse(EventMeshRetCode.EVENTMESH_ACL_ERR, responseHeaderMap, responseBodyMap, null);
                    return;
                }
            }
        }

        // validate URL
        // try {
        // if (!IPUtils.isValidDomainOrIp(url, eventMeshHttpConfiguration.getEventMeshIpv4BlackList(),
        // eventMeshHttpConfiguration.getEventMeshIpv6BlackList())) {
        // httpLogger.error("subscriber url {} is not valid", url);
        // handlerSpecific.sendErrorResponse(EventMeshRetCode.EVENTMESH_PROTOCOL_BODY_ERR, responseHeaderMap,
        // responseBodyMap, null);
        // return;
        // }
        // } catch (Exception e) {
        // httpLogger.error("subscriber url {} is not valid, error {}", url, e.getMessage());
        // handlerSpecific.sendErrorResponse(EventMeshRetCode.EVENTMESH_PROTOCOL_BODY_ERR, responseHeaderMap,
        // responseBodyMap, null);
        // return;
        // }
        //
        // CloseableHttpClient closeableHttpClient = eventMeshHTTPServer.getHttpClientPool().getClient();
        // // obtain webhook delivery agreement for Abuse Protection
        // boolean isWebhookAllowed = WebhookUtil.obtainDeliveryAgreement(closeableHttpClient,
        // url, eventMeshHttpConfiguration.getEventMeshWebhookOrigin());
        //
        // if (!isWebhookAllowed) {
        // httpLogger.error("subscriber url {} is not allowed by the target system", url);
        // handlerSpecific.sendErrorResponse(EventMeshRetCode.EVENTMESH_PROTOCOL_BODY_ERR, responseHeaderMap,
        // responseBodyMap, null);
        // return;
        // }

        long startTime = System.currentTimeMillis();
        try {
            // local subscription url
            String localUrl = "http://" + localAddress + ":"
                + eventMeshHttpConfiguration.getHttpServerPort()
                + RequestURI.PUBLISH_BRIDGE.getRequestURI();
            Map<String, Object> remoteBodyMap = new HashMap<>();
            remoteBodyMap.put(EventMeshConstants.URL, localUrl);
            remoteBodyMap.put(EventMeshConstants.CONSUMER_GROUP, eventMeshHttpConfiguration.getMeshGroup());
            remoteBodyMap.put(EventMeshConstants.MANAGE_TOPIC, requestBodyMap.get(EventMeshConstants.MANAGE_TOPIC));

            String targetMesh = requestBodyMap.get("remoteMesh") == null ? "" : requestBodyMap.get("remoteMesh").toString();

            // Get mesh address from registry
            String meshAddress = getTargetMesh(eventMeshHttpConfiguration.getMeshGroup(), subscriptionList);
            if (StringUtils.isNotBlank(meshAddress)) {
                targetMesh = meshAddress;
            }

            CloseableHttpClient closeableHttpClient = eventMeshHTTPServer.getHttpClientPool().getClient();
            String remoteResult = post(closeableHttpClient, targetMesh, builderRemoteHeaderMap(localAddress), remoteBodyMap,
                response -> EntityUtils.toString(response.getEntity(), Constants.DEFAULT_CHARSET));

            // send by QUIC protocol
            postQuic(targetMesh, builderRemoteHeaderMap(localAddress), remoteBodyMap);

            Map<String, String> remoteResultMap = Optional.ofNullable(JsonUtils.parseTypeReferenceObject(
                remoteResult,
                new TypeReference<Map<String, String>>() {
                })).orElseGet(Maps::newHashMap);

            if (String.valueOf(EventMeshRetCode.SUCCESS.getRetCode()).equals(remoteResultMap.get(EventMeshConstants.RET_CODE))) {
                responseBodyMap.put(EventMeshConstants.RET_CODE, EventMeshRetCode.SUCCESS.getRetCode());
                responseBodyMap.put(EventMeshConstants.RET_MSG, EventMeshRetCode.SUCCESS.getErrMsg());

                handlerSpecific.sendResponse(responseHeaderMap, responseBodyMap);
            } else {
                handlerSpecific.sendErrorResponse(EventMeshRetCode.EVENTMESH_SUBSCRIBE_ERR, responseHeaderMap,
                    responseBodyMap, null);
            }

        } catch (Exception e) {
            long endTime = System.currentTimeMillis();
            httpLogger.error("subscribe Remote|cost={}ms|topic={}", endTime - startTime,
                JsonUtils.toJSONString(subscriptionList), e);
            handlerSpecific.sendErrorResponse(EventMeshRetCode.EVENTMESH_SUBSCRIBE_ERR, responseHeaderMap,
                responseBodyMap, null);
        }
    }

    private void postQuic(String targetMesh, Map<String, String> requestBody, Map<String, Object> responseBody) {
        QuicSslContext context = QuicSslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build();
        NioEventLoopGroup group = new NioEventLoopGroup(1);
        try {
            ChannelHandler codec = new QuicClientCodecBuilder()
                .sslContext(context)
                .maxIdleTimeout(5000, TimeUnit.MILLISECONDS)
                .initialMaxData(10000000)
                .initialMaxStreamDataBidirectionalLocal(1000000)
                .build();

            Bootstrap bs = new Bootstrap();
            Channel channel = bs.group(group)
                .channel(NioDatagramChannel.class)
                .handler(codec)
                .bind(0).sync().channel();

            QuicChannel quicChannel = QuicChannel.newBootstrap(channel)
                .streamHandler(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelActive(ChannelHandlerContext ctx) {
                        String jsonStr = Optional.ofNullable(JsonUtils.toJSONString(requestBody)).orElse("");
                        ByteBuf buffer = ctx.alloc().directBuffer();
                        buffer.writeCharSequence(jsonStr, CharsetUtil.US_ASCII);
                        ctx.writeAndFlush(buffer);
                    }
                })
                .remoteAddress(new InetSocketAddress(targetMesh, 9999))
                .connect()
                .get();

            quicChannel.closeFuture().sync();
            channel.close().sync();
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            group.shutdownGracefully();
        }
    }

    @Override
    public String[] paths() {
        return new String[]{RequestURI.SUBSCRIBE_REMOTE.getRequestURI()};
    }

}
