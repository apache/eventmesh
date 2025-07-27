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

package cn.webank.eventmesh.client.tcp.impl;

import cn.webank.eventmesh.client.tcp.SimplePubClient;
import cn.webank.eventmesh.client.tcp.common.AsyncRRCallback;
import cn.webank.eventmesh.client.tcp.common.MessageUtils;
import cn.webank.eventmesh.client.tcp.common.ReceiveMsgHook;
import cn.webank.eventmesh.client.tcp.common.RequestContext;
import cn.webank.eventmesh.client.tcp.common.TcpClient;
import cn.webank.eventmesh.client.tcp.common.WemqAccessCommon;
import cn.webank.eventmesh.common.protocol.tcp.Command;
import cn.webank.eventmesh.common.protocol.tcp.UserAgent;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import cn.webank.eventmesh.common.protocol.tcp.Package;

/**
 * Created by nanoxiong on 2017/4/25.
 */
public class SimplePubClientImpl extends TcpClient implements SimplePubClient {

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    private UserAgent userAgent;

    private ReceiveMsgHook callback;

    private ConcurrentHashMap<String, AsyncRRCallback> callbackConcurrentHashMap = new ConcurrentHashMap<String, AsyncRRCallback>();
    private ScheduledFuture<?> task;

    public SimplePubClientImpl(String accessIp, int port, UserAgent agent) {
        super(accessIp, port);
        this.userAgent = agent;
    }

    public void registerBusiHandler(ReceiveMsgHook handler) throws Exception {
        callback = handler;
    }

    public void init() throws Exception {
        open(new Handler());
        hello();
        logger.info("SimplePubClientImpl|{}|started!", clientNo);
    }

    public void reconnect() throws Exception {
        super.reconnect();
        hello();
    }

    public void close() {
        try {
            task.cancel(false);
            goodbye();
            super.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void heartbeat() throws Exception {
        task = scheduler.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                try {
                    if (!isActive()) {
                        SimplePubClientImpl.this.reconnect();
                    }
                    Package msg = MessageUtils.heartBeat();
                    io(msg, WemqAccessCommon.DEFAULT_TIME_OUT_MILLS);
                } catch (Exception e) {
                }
            }
        }, WemqAccessCommon.HEATBEAT, WemqAccessCommon.HEATBEAT, TimeUnit.MILLISECONDS);
    }

    private void goodbye() throws Exception {
        Package msg = MessageUtils.goodbye();
        this.io(msg, WemqAccessCommon.DEFAULT_TIME_OUT_MILLS);
    }

    private void hello() throws Exception {
        Package msg = MessageUtils.hello(userAgent);
        this.io(msg, WemqAccessCommon.DEFAULT_TIME_OUT_MILLS);
    }

    /**
     * 发送RR消息
     *
     * @param msg
     * @param timeout
     * @return
     * @throws Exception
     */
    public Package rr(Package msg, long timeout) throws Exception {
        logger.info("SimplePubClientImpl|{}|rr|send|type={}|msg={}", clientNo, msg.getHeader().getCommand(), msg);
        return io(msg, timeout);
    }

    /**
     * 异步RR
     *
     * @param msg
     * @param callback
     * @param timeout
     * @throws Exception
     */
    @Override
    public void asyncRR(Package msg, AsyncRRCallback callback, long timeout) throws Exception {
        super.send(msg);
        this.callbackConcurrentHashMap.put((String) RequestContext._key(msg), callback);

    }

    /**
     * 发送事件消息, 只所以有返回值是ACCESS 给了ACK
     *
     * @param msg
     * @throws Exception
     */
    public Package publish(Package msg, long timeout) throws Exception {
        logger.info("SimplePubClientImpl|{}|publish|send|type={}|msg={}", clientNo, msg.getHeader().getCommand(), msg);
        return io(msg, timeout);
    }

    /**
     * 发送广播消息
     *
     * @param msg
     * @param timeout
     * @throws Exception
     */
    public void broadcast(Package msg, long timeout) throws Exception {
        logger.info("SimplePubClientImpl|{}|broadcast|send|type={}|msg={}", clientNo, msg.getHeader().getCommand(), msg);
        super.send(msg);
    }

    @Override
    public UserAgent getUserAgent() {
        return userAgent;
    }

    @ChannelHandler.Sharable
    private class Handler extends SimpleChannelInboundHandler<Package> {
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Package msg) throws Exception {
            logger.info("SimplePubClientImpl|{}|receive|type={}|msg={}", clientNo, msg.getHeader(), msg);
            if (callback != null) {
                callback.handle(msg, ctx);
            }

            Command cmd = msg.getHeader().getCommand();
            if (cmd == Command.RESPONSE_TO_CLIENT) {
                Package pkg = MessageUtils.responseToClientAck(msg);
                send(pkg);
            } else if (cmd == Command.SERVER_GOODBYE_REQUEST) {

            }

            RequestContext context = contexts.get(RequestContext._key(msg));
            if (context != null) {
                contexts.remove(context.getKey());
                context.finish(msg);
                return;
            } else {
                return;
            }
        }
    }

    @Override
    public String toString() {
        return "SimplePubClientImpl|clientNo=" + clientNo + "|" + userAgent;
    }
}
