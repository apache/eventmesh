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

package client.impl;

import client.SubClient;
import client.common.ClientConstants;
import client.common.MessageUtils;
import client.common.RequestContext;
import client.common.TCPClient;
import client.hook.ReceiveMsgHook;
import cn.webank.eventmesh.common.protocol.tcp.Command;
import cn.webank.eventmesh.common.protocol.tcp.OPStatus;
import cn.webank.eventmesh.common.protocol.tcp.Package;
import cn.webank.eventmesh.common.protocol.tcp.UserAgent;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.apache.commons.collections4.CollectionUtils;
import org.junit.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class SubClientImpl extends TCPClient implements SubClient {

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    private UserAgent userAgent;

    private ReceiveMsgHook callback;

    private List<String> topics = new ArrayList<String>();

    private ScheduledFuture<?> task;

    public SubClientImpl(String accessIp, int port, UserAgent agent) {
        super(accessIp, port);
        this.userAgent = agent;
    }

    public void registerBusiHandler(ReceiveMsgHook handler) throws Exception {
        callback = handler;
    }

    public void init() throws Exception {
        open(new Handler());
        hello();
        logger.info("SubClientImpl|{}|started!", clientNo);
    }

    public void reconnect() throws Exception {
        super.reconnect();
        hello();
        if (!CollectionUtils.isEmpty(topics)) {
            for (String topic : topics) {
                Package request = MessageUtils.subscribe(topic);
                this.dispatcher(request, ClientConstants.DEFAULT_TIMEOUT_IN_MILLISECONDS);
            }
        }
        listen();
    }

    public void close() {
        try {
            task.cancel(false);
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
                        SubClientImpl.this.reconnect();
                    }
                    Package msg = MessageUtils.heartBeat();
                    logger.debug("SubClientImpl|{}|send heartbeat|Command={}|msg={}", clientNo, msg.getHeader().getCommand(), msg);
                    SubClientImpl.this.dispatcher(msg, ClientConstants.DEFAULT_TIMEOUT_IN_MILLISECONDS);
                } catch (Exception e) {
                }
            }
        }, ClientConstants.HEARTBEAT, ClientConstants.HEARTBEAT, TimeUnit.MILLISECONDS);
    }

    public Package goodbye() throws Exception {
        Package msg = MessageUtils.goodbye();
        return this.io(msg, ClientConstants.DEFAULT_TIMEOUT_IN_MILLISECONDS);
    }

    private void hello() throws Exception {
        Package msg = MessageUtils.hello(userAgent);
        this.dispatcher(msg, ClientConstants.DEFAULT_TIMEOUT_IN_MILLISECONDS);
    }

    public Package justSubscribe(String serviceId, String scenario, String dcn) throws Exception {
        Package msg = MessageUtils.subscribe();
        return this.dispatcher(msg, ClientConstants.DEFAULT_TIMEOUT_IN_MILLISECONDS);
    }

    public Package justSubscribe(String topic) throws Exception {
        topics.add(topic);
        Package msg = MessageUtils.subscribe(topic);
        return this.dispatcher(msg, ClientConstants.DEFAULT_TIMEOUT_IN_MILLISECONDS);
    }

    public Package listen() throws Exception {
        Package request = MessageUtils.listen();
        return this.dispatcher(request, ClientConstants.DEFAULT_TIMEOUT_IN_MILLISECONDS);
    }

//    @Override
//    public void traceLog() throws Exception {
//        Package msg = MessageUtils.traceLog();
//        this.dispatcher(msg, ClientConstants.DEFAULT_TIMEOUT_IN_MILLISECONDS);
//    }

//    public void sysLog() throws Exception {
//        Package msg = MessageUtils.sysLog();
//        this.dispatcher(msg, ClientConstants.DEFAULT_TIMEOUT_IN_MILLISECONDS);
//    }

    public Package justUnsubscribe(String topic) throws Exception {
        topics.remove(topic);
        Package msg = MessageUtils.unsubscribe(topic);
        return this.dispatcher(msg, ClientConstants.DEFAULT_TIMEOUT_IN_MILLISECONDS);
    }

    public Package justUnsubscribe(String serviceId, String scenario, String dcn) throws Exception {
        Package msg = MessageUtils.unsubscribe();
        return this.dispatcher(msg, ClientConstants.DEFAULT_TIMEOUT_IN_MILLISECONDS);
    }

    public UserAgent getUserAgent() {
        return userAgent;
    }

    public Package dispatcher(Package request, long timeout) throws Exception {
        Assert.assertNotNull(request);
        Package response = super.io(request, timeout);
        switch (request.getHeader().getCommand()) {
            case HELLO_REQUEST:
                Assert.assertEquals(response.getHeader().getCommand(), Command.HELLO_RESPONSE);
                break;
            case HEARTBEAT_REQUEST:
                Assert.assertEquals(response.getHeader().getCommand(), Command.HEARTBEAT_RESPONSE);
                break;
            case LISTEN_REQUEST:
                Assert.assertEquals(response.getHeader().getCommand(), Command.LISTEN_RESPONSE);
                break;
            case CLIENT_GOODBYE_REQUEST:
                Assert.assertEquals(response.getHeader().getCommand(), Command.CLIENT_GOODBYE_RESPONSE);
                break;
            case SUBSCRIBE_REQUEST:
                Assert.assertEquals(response.getHeader().getCommand(), Command.SUBSCRIBE_RESPONSE);
                break;
            case UNSUBSCRIBE_REQUEST:
                Assert.assertEquals(response.getHeader().getCommand(), Command.UNSUBSCRIBE_RESPONSE);
                break;
            case SYS_LOG_TO_LOGSERVER:
                Assert.assertNull(response);
                break;
            case TRACE_LOG_TO_LOGSERVER:
                Assert.assertNull(response);
                break;
            default:
                break;
        }
        if (response != null) {
            assert response.getHeader().getCode() == OPStatus.SUCCESS.getCode();
        }
        return response;
    }

    @ChannelHandler.Sharable
    private class Handler extends SimpleChannelInboundHandler<Package> {
        @SuppressWarnings("Duplicates")
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Package msg) throws Exception {
            logger.info(SubClientImpl.class.getSimpleName() + "|receive|command={}|msg={}", msg.getHeader().getCommand(), msg);
            Command cmd = msg.getHeader().getCommand();
            if (callback != null) {
                callback.handle(msg, ctx);
            }
            if (cmd == Command.REQUEST_TO_CLIENT) {
                try {
                    Package ackMsg = MessageUtils.requestToClientAck(msg);
                    send(ackMsg);
                    Package aPackage = MessageUtils.rrResponse(msg);
                    send(aPackage);
                } catch (Exception e) {
                    logger.info("send rr request to client ack failed");
                }
            } else if (cmd == Command.ASYNC_MESSAGE_TO_CLIENT) {
                Package asyncAck = MessageUtils.asyncMessageAck(msg);
                try {
                    send(asyncAck);
                } catch (Exception e) {
                    logger.info("send async request to client ack failed");
                }
            } else if (cmd == Command.BROADCAST_MESSAGE_TO_CLIENT) {
                Package broadcastAck = MessageUtils.broadcastMessageAck(msg);
                try {
                    send(broadcastAck);
                } catch (Exception e) {
                    logger.info("send broadcast request to client ack failed");
                }
            } else if (cmd == Command.SERVER_GOODBYE_REQUEST) {
                System.err.println("server goodby request: ---------------------------" + msg.toString());
                close();
            } else {
                //控制指令集
                RequestContext context = contexts.get(RequestContext._key(msg));
                if (context != null) {
                    contexts.remove(context.getKey());
                    context.finish(msg);
                    return;
                } else {
                    logger.error("msg ignored,context not found.|{}|{}", cmd, msg);
                    return;
                }
            }
        }
    }

    @Override
    public String toString() {
        return "SubClientImpl|clientNo=" + clientNo + "|" + userAgent;
    }
}
