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

package org.apache.eventmesh.client.tcp.impl;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import io.cloudevents.CloudEvent;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

import org.apache.eventmesh.client.tcp.SimplePubClient;
import org.apache.eventmesh.client.tcp.common.AsyncRRCallback;
import org.apache.eventmesh.client.tcp.common.EventMeshCommon;
import org.apache.eventmesh.client.tcp.common.MessageUtils;
import org.apache.eventmesh.client.tcp.common.ReceiveMsgHook;
import org.apache.eventmesh.client.tcp.common.RequestContext;
import org.apache.eventmesh.client.tcp.common.TcpClient;
import org.apache.eventmesh.common.protocol.tcp.Command;
import org.apache.eventmesh.common.protocol.tcp.Package;
import org.apache.eventmesh.common.protocol.tcp.UserAgent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SimplePubClientImpl extends TcpClient implements SimplePubClient {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

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
                    io(msg, EventMeshCommon.DEFAULT_TIME_OUT_MILLS);
                } catch (Exception ignore) {
                }
            }
        }, EventMeshCommon.HEARTBEAT, EventMeshCommon.HEARTBEAT, TimeUnit.MILLISECONDS);
    }

    private void goodbye() throws Exception {
        Package msg = MessageUtils.goodbye();
        this.io(msg, EventMeshCommon.DEFAULT_TIME_OUT_MILLS);
    }

    private void hello() throws Exception {
        Package msg = MessageUtils.hello(userAgent);
        this.io(msg, EventMeshCommon.DEFAULT_TIME_OUT_MILLS);
    }

    /**
     * Send RR message
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
     * Asynchronous RR
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
     * Publish message
     *
     * @param msg
     * @throws Exception
     */
    public Package publish(Package msg, long timeout) throws Exception {
        logger.info("SimplePubClientImpl|{}|publish|send|type={}|msg={}", clientNo, msg.getHeader().getCommand(), msg);
        return io(msg, timeout);
    }


    @Override
    public Package publish(CloudEvent cloudEvent, long timeout) throws Exception {
        Package msg = MessageUtils.asyncCloudEvent(cloudEvent);
        logger.info("SimplePubClientImpl|{}|publish|send|type={}|msg={}", clientNo,
            msg.getHeader().getCommand(), msg);
        return io(MessageUtils.asyncCloudEvent(cloudEvent), timeout);
    }

    @Override
    public void broadcast(CloudEvent cloudEvent, long timeout) throws Exception {
        Package msg = MessageUtils.asyncCloudEvent(cloudEvent);
        logger.info("SimplePubClientImpl|{}|publish|send|type={}|msg={}", clientNo,
            msg.getHeader().getCommand(), msg);
        super.send(msg);
    }

    /**
     * Send broadcast message
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

            Command cmd = msg.getHeader().getCommand();
            if (cmd == Command.RESPONSE_TO_CLIENT) {
                if (callback != null) {
                    callback.handle(msg, ctx);
                }
                Package pkg = MessageUtils.responseToClientAck(msg);
                send(pkg);
            } else if (cmd == Command.SERVER_GOODBYE_REQUEST) {
                //TODO
            }

            RequestContext context = contexts.get(RequestContext._key(msg));
            if (context != null) {
                contexts.remove(context.getKey());
                context.finish(msg);
            }
        }
    }

    @Override
    public String toString() {
        return "SimplePubClientImpl|clientNo=" + clientNo + "|" + userAgent;
    }
}
