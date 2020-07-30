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

package cn.webank.emesher.core.protocol.tcp.client;

import cn.webank.emesher.boot.ProxyTCPServer;
import cn.webank.emesher.core.protocol.tcp.client.group.ClientSessionGroupMapping;
import cn.webank.emesher.core.protocol.tcp.client.session.Session;
import cn.webank.emesher.core.protocol.tcp.client.session.SessionState;
import cn.webank.eventmesh.common.protocol.tcp.Header;
import cn.webank.eventmesh.common.protocol.tcp.OPStatus;
import cn.webank.eventmesh.common.protocol.tcp.Package;
import cn.webank.eventmesh.common.protocol.tcp.RedirectInfo;
import cn.webank.emesher.metrics.tcp.ProxyTcpMonitor;
import cn.webank.emesher.util.Utils;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import org.apache.rocketmq.remoting.common.RemotingHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

import static cn.webank.eventmesh.common.protocol.tcp.Command.REDIRECT_TO_CLIENT;
import static cn.webank.eventmesh.common.protocol.tcp.Command.SERVER_GOODBYE_REQUEST;

public class ProxyTcp2Client {

    private static final Logger logger = LoggerFactory.getLogger(ProxyTcp2Client.class);

    public static InetSocketAddress serverGoodby2Client(Session session, ClientSessionGroupMapping mapping) {
        logger.info("serverGoodby2Client client[{}]", session.getClient());
        try{
            long startTime = System.currentTimeMillis();
            Package msg = new Package();
            msg.setHeader(new Header(SERVER_GOODBYE_REQUEST, OPStatus.SUCCESS.getCode(), "graceful normal quit from access",
                    null));

            //session.write2Client(msg);
            ProxyTCPServer.scheduler.submit(new Runnable() {
                @Override
                public void run() {
                    long taskExecuteTime = System.currentTimeMillis();
                    Utils.writeAndFlush(msg, startTime, taskExecuteTime, session.getContext(), session);
                }
            });
            InetSocketAddress address = (InetSocketAddress) session.getContext().channel().remoteAddress();

            closeSessionIfTimeout(session, mapping);
            return address;
        }catch (Exception e){
            logger.error("exception occur while serverGoodby2Client", e);
            return null;
        }
    }

    public static InetSocketAddress goodBye2Client(Session session,
                                                   String errMsg,
                                                   int proxyStatus,
                                                   ClientSessionGroupMapping mapping) {
        try {
            long startTime = System.currentTimeMillis();
            Package msg = new Package();
            msg.setHeader(new Header(SERVER_GOODBYE_REQUEST, proxyStatus, errMsg,null));
            ProxyTCPServer.scheduler.schedule(new Runnable() {
                @Override
                public void run() {
                    long taskExecuteTime = System.currentTimeMillis();
                    Utils.writeAndFlush(msg, startTime, taskExecuteTime, session.getContext(), session);
                }
            }, 1 * 1000, TimeUnit.MILLISECONDS);

            closeSessionIfTimeout(session, mapping);

            return session.getRemoteAddress();
        } catch (Exception e) {
            logger.error("exception occur while goodbye2client", e);
            return null;
        }
    }

    public static void goodBye2Client(ChannelHandlerContext ctx,
                                      String errMsg,
                                      ClientSessionGroupMapping mapping,
                                      ProxyTcpMonitor proxyTcpMonitor) {
        long startTime = System.currentTimeMillis();
        Package pkg = new Package(new Header(SERVER_GOODBYE_REQUEST, OPStatus.FAIL.getCode(),
                errMsg, null));
        proxyTcpMonitor.getProxy2clientMsgNum().incrementAndGet();
        logger.info("goodBye2Client client[{}]", RemotingHelper.parseChannelRemoteAddr(ctx.channel()));
        ctx.writeAndFlush(pkg).addListener(
                new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) throws Exception {
                        Utils.logSucceedMessageFlow(pkg, null, startTime, startTime);
                        try {
                            mapping.closeSession(ctx);
                        } catch (Exception e) {
                            logger.warn("close session failed!", e);
                        }
                    }
                }
        );
    }

    public static String redirectClient2NewProxy(String newProxyIp, int port, Session session, ClientSessionGroupMapping mapping) {
        logger.info("begin to gracefully redirect Client {}, newIPPort[{}]", session.getClient(), newProxyIp + ":" + port);
        try {
            long startTime = System.currentTimeMillis();

            Package pkg = new Package();
            pkg.setHeader(new Header(REDIRECT_TO_CLIENT, OPStatus.SUCCESS.getCode(), null,
                    null));
            pkg.setBody(new RedirectInfo(newProxyIp, port));
            ProxyTCPServer.scheduler.schedule(new Runnable() {
                @Override
                public void run() {
                    long taskExecuteTime = System.currentTimeMillis();
                    Utils.writeAndFlush(pkg, startTime, taskExecuteTime, session.getContext(), session);
                }
            }, 5 * 1000, TimeUnit.MILLISECONDS);
            closeSessionIfTimeout(session, mapping);
            return session.getRemoteAddress() + "--->" + newProxyIp + ":" + port;
        } catch (Exception e) {
            logger.error("exception occur while redirectClient2NewProxy", e);
            return null;
        }
    }

    public static void closeSessionIfTimeout(Session session, ClientSessionGroupMapping mapping) {
        ProxyTCPServer.scheduler.schedule(new Runnable() {
            @Override
            public void run() {
                try {
                    if(!session.getSessionState().equals(SessionState.CLOSED)){
                        mapping.closeSession(session.getContext());
                        logger.info("closeSessionIfTimeout success, session[{}]", session.getClient());
                    }
                } catch (Exception e) {
                    logger.error("close session failed", e);
                }
            }
        }, 30 * 1000, TimeUnit.MILLISECONDS);
    }
}
