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

package org.apache.eventmesh.runtime.core.protocol.tcp.client.processor;

import org.apache.eventmesh.common.protocol.tcp.Command;
import org.apache.eventmesh.common.protocol.tcp.Package;
import org.apache.eventmesh.runtime.acl.Acl;
import org.apache.eventmesh.runtime.boot.EventMeshTCPServer;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.session.Session;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.session.push.DownStreamMsgContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.channel.ChannelHandlerContext;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MessageAckProcessor implements TcpProcessor {

    private static final Logger MESSAGE_LOGGER = LoggerFactory.getLogger(EventMeshConstants.MESSAGE);
    private EventMeshTCPServer eventMeshTCPServer;
    private final Acl acl;

    public MessageAckProcessor(EventMeshTCPServer eventMeshTCPServer) {
        this.eventMeshTCPServer = eventMeshTCPServer;
        this.acl = eventMeshTCPServer.getAcl();
    }

    @Override
    public void process(final Package pkg, final ChannelHandlerContext ctx, long startTime) {
        Session session = eventMeshTCPServer.getClientSessionGroupMapping().getSession(ctx);
        long taskExecuteTime = System.currentTimeMillis();
        String seq = pkg.getHeader().getSeq();
        Command cmd = pkg.getHeader().getCmd();

        if (seq == null) {
            log.error("MessageAckTask failed, seq cannot be null|user={}", session.getClient());
            return;
        }
        DownStreamMsgContext downStreamMsgContext = session.getPusher().getUnAckMsg().get(seq);
        // ack non-broadcast msg
        if (downStreamMsgContext != null) {
            downStreamMsgContext.ackMsg();
            session.getPusher().getUnAckMsg().remove(seq);
        } else {
            if (cmd != Command.RESPONSE_TO_CLIENT_ACK) {
                log.warn("MessageAckTask, seq:{}, downStreamMsgContext not in downStreamMap,client:{}",
                        seq, session.getClient());
            }
        }
        MESSAGE_LOGGER.info("pkg|c2eventMesh|cmd={}|seq=[{}]|user={}|wait={}ms|cost={}ms", cmd, seq, session.getClient(),
                taskExecuteTime - startTime, System.currentTimeMillis() - startTime);
    }
}
