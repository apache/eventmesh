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

package cn.webank.emesher.core.protocol.tcp.client.task;

import cn.webank.emesher.boot.ProxyTCPServer;
import cn.webank.emesher.core.protocol.tcp.client.session.Session;
import cn.webank.eventmesh.common.protocol.tcp.Package;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractTask implements Runnable {

    protected final Logger logger = LoggerFactory.getLogger(this.getClass());
    protected Package pkg;
    protected ChannelHandlerContext ctx;
    protected Session session;
    protected long startTime;
    protected ProxyTCPServer proxyTCPServer;

    public AbstractTask(Package pkg, ChannelHandlerContext ctx, long startTime, ProxyTCPServer proxyTCPServer) {
        this.proxyTCPServer = proxyTCPServer;
        this.pkg = pkg;
        this.ctx = ctx;
        this.session = proxyTCPServer.getClientSessionGroupMapping().getSession(ctx);
        this.startTime = startTime;
    }
}
