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

package org.apache.eventmesh.runtime.core.protocol.tcp.client.task;

import org.apache.eventmesh.common.protocol.tcp.Command;
import org.apache.eventmesh.common.protocol.tcp.Package;
import org.apache.eventmesh.runtime.boot.EventMeshTCPServer;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.session.push.DownStreamMsgContext;
import org.apache.eventmesh.runtime.trace.AttributeKeys;
import org.apache.eventmesh.trace.api.EventMeshSpan;
import org.apache.eventmesh.trace.api.EventMeshTraceContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.channel.ChannelHandlerContext;

public class MessageAckTask extends AbstractTask {

    private final Logger messageLogger = LoggerFactory.getLogger("message");

    public MessageAckTask(Package pkg, ChannelHandlerContext ctx, long startTime, EventMeshTCPServer eventMeshTCPServer) {
        super(pkg, ctx, startTime, eventMeshTCPServer);
    }

    @Override
    public void run() {
        long taskExecuteTime = System.currentTimeMillis();

        EventMeshSpan span = null;
        try{
            if (eventMeshTCPServer.getEventMeshTCPConfiguration().eventMeshServerTraceEnable) {
                EventMeshTraceContext
                    context = ctx.channel().attr(AttributeKeys.EVENTMESH_SERVER_CONTEXT).get();
                span = context.getSpan();
            }
        }catch (Exception exception){
            logger.warn("get span from ChannelHandlerContext fail", exception);
        }

        String seq = null;
        Command cmd = null;
        try {
            seq = pkg.getHeader().getSeq();
            cmd = pkg.getHeader().getCmd();

            if (seq == null) {
                logger.error("MessageAckTask failed, seq cannot be null|user={}",
                    session.getClient());
                return;
            }
            DownStreamMsgContext downStreamMsgContext = session.getPusher().getUnAckMsg().get(seq);
            // ack non-broadcast msg
            if (downStreamMsgContext != null) {
                downStreamMsgContext.ackMsg();
                session.getPusher().getUnAckMsg().remove(seq);
            } else {
                if (!cmd.equals(Command.RESPONSE_TO_CLIENT_ACK)) {
                    logger.warn(
                        "MessageAckTask, seq:{}, downStreamMsgContext not in downStreamMap,client:{}",
                        seq, session.getClient());
                }
            }
        }catch (Exception e){
            logger
                .error("MessageAckTask failed|cmd={}|seq={}|user={}", cmd, seq, session.getClient(),
                    e);
            if (eventMeshTCPServer.getEventMeshTCPConfiguration().eventMeshServerTraceEnable && span != null) {
                span.addError(e);
            }
        }finally {
            try{
                if (eventMeshTCPServer.getEventMeshTCPConfiguration().eventMeshServerTraceEnable && span != null) {
                    span.finish();
                }
            }catch (Exception e){
                logger.warn("uploadTraceLog failed in MessageAckTask", e);
            }
        }

        if (eventMeshTCPServer.getEventMeshTCPConfiguration().eventMeshServerTraceEnable && span != null) {
            span.finish();
        }

        messageLogger.info("pkg|c2eventMesh|cmd={}|seq=[{}]|user={}|wait={}ms|cost={}ms", cmd, seq, session.getClient(),
                taskExecuteTime - startTime, System.currentTimeMillis() - startTime);
    }
}
