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

package org.apache.eventmesh.client.http;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.multipart.Attribute;
import io.netty.handler.codec.http.multipart.DefaultHttpDataFactory;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.eventmesh.client.http.consumer.HandleResult;
import org.apache.eventmesh.client.http.consumer.context.LiteConsumeContext;
import org.apache.eventmesh.client.http.consumer.listener.LiteMessageListener;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.utils.IPUtil;
import org.apache.eventmesh.common.LiteMessage;
import org.apache.eventmesh.common.utils.ThreadUtil;
import org.apache.eventmesh.common.command.HttpCommand;
import org.apache.eventmesh.common.protocol.http.body.Body;
import org.apache.eventmesh.common.protocol.http.body.message.PushMessageRequestBody;
import org.apache.eventmesh.common.protocol.http.common.ClientRetCode;
import org.apache.eventmesh.common.protocol.http.common.ProtocolKey;
import org.apache.eventmesh.common.protocol.http.common.ProtocolVersion;
import org.apache.eventmesh.common.protocol.http.common.RequestCode;
import org.apache.eventmesh.common.protocol.http.header.Header;
import org.apache.eventmesh.common.protocol.http.header.message.PushMessageRequestHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RemotingServer {

    public Logger logger = LoggerFactory.getLogger(this.getClass());

    public AtomicBoolean started = new AtomicBoolean(Boolean.FALSE);

    public AtomicBoolean inited = new AtomicBoolean(Boolean.FALSE);

    private EventLoopGroup bossGroup;

    private EventLoopGroup workerGroup;

    private int port = RandomUtils.nextInt(1000, 20000);

    private DefaultHttpDataFactory defaultHttpDataFactory = new DefaultHttpDataFactory(false);

    private ThreadPoolExecutor consumeExecutor;

    private LiteMessageListener messageListener;

    public RemotingServer() {
    }

    public RemotingServer(int port) {
        this.port = port;
    }

    public RemotingServer(ThreadPoolExecutor consumeExecutor) {
        this.consumeExecutor = consumeExecutor;
    }

    public RemotingServer(int port, ThreadPoolExecutor consumeExecutor) {
        this.port = port;
        this.consumeExecutor = consumeExecutor;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public void setConsumeExecutor(ThreadPoolExecutor consumeExecutor) {
        this.consumeExecutor = consumeExecutor;
    }

    // TODO: Let different topics have different listeners
    public void registerMessageListener(LiteMessageListener eventMeshMessageListener) {
        this.messageListener = eventMeshMessageListener;
    }

    private EventLoopGroup initBossGroup() {
        bossGroup = new NioEventLoopGroup(1, new ThreadFactory() {
            AtomicInteger count = new AtomicInteger(0);

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "endPointBoss-" + count.incrementAndGet());
                t.setDaemon(true);
                return t;
            }
        });

        return bossGroup;
    }

    private EventLoopGroup initWokerGroup() {
        workerGroup = new NioEventLoopGroup(2, new ThreadFactory() {
            AtomicInteger count = new AtomicInteger(0);

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "endpointWorker-" + count.incrementAndGet());
                return t;
            }
        });
        return workerGroup;
    }

    public String getEndpointURL() {
        return String.format("http://%s:%s", IPUtil.getLocalAddress(), port);
    }


    class HTTPHandler extends SimpleChannelInboundHandler<HttpRequest> {

        /**
         * Parse request HEADER
         *
         * @param fullReq
         * @return
         */
        private Map<String, Object> parseHTTPHeader(HttpRequest fullReq) {
            Map<String, Object> headerParam = new HashMap<>();
            for (String key : fullReq.headers().names()) {
                if (StringUtils.equalsIgnoreCase(HttpHeaderNames.CONTENT_TYPE.toString(), key)
                        || StringUtils.equalsIgnoreCase(HttpHeaderNames.ACCEPT_ENCODING.toString(), key)
                        || StringUtils.equalsIgnoreCase(HttpHeaderNames.CONTENT_LENGTH.toString(), key)) {
                    continue;
                }
                headerParam.put(key, fullReq.headers().get(key));
            }
            return headerParam;
        }

        @Override
        protected void channelRead0(final ChannelHandlerContext ctx, HttpRequest httpRequest) throws Exception {
            HttpPostRequestDecoder decoder = null;
            try {
                if (!httpRequest.decoderResult().isSuccess()) {
                    sendError(ctx, HttpResponseStatus.BAD_REQUEST);
                    return;
                }

                // Protocol version verification
                String protocolVersion = StringUtils.deleteWhitespace(httpRequest.headers().get(ProtocolKey.VERSION));
                if (StringUtils.isBlank(protocolVersion) || !ProtocolVersion.contains(protocolVersion)) {
                    httpRequest.headers().set(ProtocolKey.VERSION, ProtocolVersion.V1.getVersion());
                }

                Map<String, Object> bodyMap = new HashMap<>();

                if (httpRequest.method() == HttpMethod.GET) {
                    QueryStringDecoder getDecoder = new QueryStringDecoder(httpRequest.uri());
                    for (Map.Entry<String, List<String>> entry : getDecoder.parameters().entrySet()) {
                        bodyMap.put(entry.getKey(), entry.getValue().get(0));
                    }
                } else if (httpRequest.method() == HttpMethod.POST) {
                    decoder = new HttpPostRequestDecoder(defaultHttpDataFactory, httpRequest);
                    List<InterfaceHttpData> parmList = decoder.getBodyHttpDatas();
                    for (InterfaceHttpData parm : parmList) {
                        if (parm.getHttpDataType() == InterfaceHttpData.HttpDataType.Attribute) {
                            Attribute data = (Attribute) parm;
                            bodyMap.put(data.getName(), data.getValue());
                        }
                    }
                } else {
                    sendError(ctx, HttpResponseStatus.METHOD_NOT_ALLOWED);
                    return;
                }

                /////////////////////////////////////////////////////////////////Basic inspection////////////////////////////////////////////////////
                String requestCode =
                        (httpRequest.method() == HttpMethod.POST) ? StringUtils.deleteWhitespace(httpRequest.headers().get(ProtocolKey.REQUEST_CODE))
                                : MapUtils.getString(bodyMap, StringUtils.lowerCase(ProtocolKey.REQUEST_CODE), "");

                final HttpCommand requestCommand = new HttpCommand(
                        httpRequest.method().name(),
                        httpRequest.protocolVersion().protocolName(), requestCode);

                HttpCommand responseCommand;

                // Verify requestCode
                if (StringUtils.isBlank(requestCode)
                        || !StringUtils.isNumeric(requestCode)
                        || (!String.valueOf(RequestCode.HTTP_PUSH_CLIENT_ASYNC.getRequestCode()).equals(requestCode)
                        && !String.valueOf(RequestCode.HTTP_PUSH_CLIENT_SYNC.getRequestCode()).equals(requestCode))) {
                    logger.error("receive invalid requestCode, {}", requestCode);
                    responseCommand = requestCommand.createHttpCommandResponse(ClientRetCode.OK.getRetCode(), ClientRetCode.OK.getErrMsg());
                    sendResponse(ctx, responseCommand.httpResponse());
                    return;
                }

                requestCommand.setHeader(Header.buildHeader(requestCode, parseHTTPHeader(httpRequest)));
                requestCommand.setBody(Body.buildBody(requestCode, bodyMap));

                if (logger.isDebugEnabled()) {
                    logger.debug("{}", requestCommand);
                }

                PushMessageRequestHeader pushMessageRequestHeader = (PushMessageRequestHeader) requestCommand.header;
                PushMessageRequestBody pushMessageRequestBody = (PushMessageRequestBody) requestCommand.body;

                String topic = pushMessageRequestBody.getTopic();

                // Check if there is a listener for the TOPIC
//                if (!listenerTable.containsKey(topic)) {
//                    logger.error("no listenning for this topic, {}", topic);
//                    responseCommand = requestCommand.createHttpCommandResponse(ClientRetCode.NOLISTEN.getRetCode(), ClientRetCode.NOLISTEN.getErrMsg());
//                    sendResponse(ctx, responseCommand.httpResponse());
//                    return;
//                }

                final LiteConsumeContext eventMeshConsumeContext = new LiteConsumeContext(pushMessageRequestHeader.getEventMeshIp(),
                        pushMessageRequestHeader.getEventMeshEnv(), pushMessageRequestHeader.getEventMeshIdc(),
                        pushMessageRequestHeader.getEventMeshCluster());

                final LiteMessage liteMessage = new LiteMessage(pushMessageRequestBody.getBizSeqNo(), pushMessageRequestBody.getUniqueId(),
                        topic, pushMessageRequestBody.getContent());

                for (Map.Entry<String, String> entry : pushMessageRequestBody.getExtFields().entrySet()) {
                    liteMessage.addProp(entry.getKey(), entry.getValue());
                }

                // Transfer to the consumer thread pool
                consumeExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            if (messageListener.reject()) {
                                HttpCommand responseCommand = requestCommand.createHttpCommandResponse(handleResult2ClientRetCode(HandleResult.RETRY).getRetCode(), handleResult2ClientRetCode(HandleResult.RETRY).getErrMsg());
                                sendResponse(ctx, responseCommand.httpResponse());
                                return;
                            }

                            HandleResult handleResult = messageListener.handle(liteMessage, eventMeshConsumeContext);

                            if (logger.isDebugEnabled()) {
                                logger.info("bizSeqNo:{}, topic:{}, handleResult:{}", liteMessage.getBizSeqNo(), liteMessage.getTopic(), handleResult);
                            }

                            HttpCommand responseCommand = requestCommand.createHttpCommandResponse(handleResult2ClientRetCode(handleResult).getRetCode(), handleResult2ClientRetCode(handleResult).getErrMsg());
                            sendResponse(ctx, responseCommand.httpResponse());
                        } catch (Exception e) {
                            logger.error("process error", e);
                        }
                    }
                });
            } catch (Exception ex) {
                logger.error("HTTPHandler.channelRead0 err", ex);
            } finally {
                try {
                    decoder.destroy();
                } catch (Exception e) {
                }
            }
        }

        public ClientRetCode handleResult2ClientRetCode(HandleResult handleResult) {
            if (handleResult == HandleResult.OK) {
                return ClientRetCode.OK;
            } else if (handleResult == HandleResult.FAIL) {
                return ClientRetCode.FAIL;
            } else if (handleResult == HandleResult.NOLISTEN) {
                return ClientRetCode.NOLISTEN;
            } else if (handleResult == HandleResult.RETRY) {
                return ClientRetCode.RETRY;
            } else {
                return ClientRetCode.OK;
            }
        }

        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
            super.channelReadComplete(ctx);
            ctx.flush(); // 4
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            if (null != cause) cause.printStackTrace();
            if (null != ctx) ctx.close();
        }

        /**
         * Send default error page
         *
         * @param ctx
         * @param status
         */
        private void sendError(ChannelHandlerContext ctx,
                               HttpResponseStatus status) {
            FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
                    status);
            response.headers().add(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_PLAIN +
                    "; charset=" + Constants.DEFAULT_CHARSET);
            response.headers().add(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
            response.headers().add(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
            ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
        }


        /**
         * Send response
         *
         * @param ctx
         * @param response
         */
        private void sendResponse(ChannelHandlerContext ctx,
                                  DefaultFullHttpResponse response) {
            ctx.writeAndFlush(response).addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture f) throws Exception {
                    if (!f.isSuccess()) {
                        logger.warn("send response to [{}] fail, will close this channel", IPUtil.parseChannelRemoteAddr(f.channel()));
                        f.channel().close();
                        return;
                    }
                }
            });
        }

        public void shutdown() throws Exception {
            if (bossGroup != null) {
                bossGroup.shutdownGracefully();
            }

            ThreadUtil.randomSleep(30);

            if (workerGroup != null) {
                workerGroup.shutdownGracefully();
            }

            started.compareAndSet(true, false);
            inited.compareAndSet(true, false);
        }
    }

    public void init() throws Exception {
        initBossGroup();
        initWokerGroup();
        inited.compareAndSet(false, true);
    }

    public void start() throws Exception {
        Runnable r = new Runnable() {
            @Override
            public void run() {
                ServerBootstrap b = new ServerBootstrap();
                b.group(bossGroup, workerGroup)
                        .channel(NioServerSocketChannel.class)
                        .childHandler(new ChannelInitializer<SocketChannel>() {
                            @Override
                            public void initChannel(SocketChannel ch)
                                    throws Exception {
                                ch.pipeline()
                                        .addLast(new HttpRequestDecoder(),
                                                new HttpResponseEncoder(),
                                                new HttpObjectAggregator(Integer.MAX_VALUE),
                                                new HTTPHandler());        // 4
                            }
                        }).childOption(ChannelOption.SO_KEEPALIVE, Boolean.TRUE);
                try {
                    logger.info("EventMesh Client[{}] Started......", port);
                    ChannelFuture future = b.bind(port).sync();
                    future.channel().closeFuture().sync();
                    started.compareAndSet(false, true);
                } catch (Exception e) {
                    bossGroup.shutdownGracefully();
                    workerGroup.shutdownGracefully();
                }
            }
        };


        Thread t = new Thread(r, "eventMesh-client-remoting-server");
        t.start();
    }
}
