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

package org.apache.eventmesh.protocol.tcp.task;

import io.netty.channel.ChannelHandlerContext;
import org.apache.eventmesh.common.protocol.tcp.Command;
import org.apache.eventmesh.common.protocol.tcp.Package;
import org.apache.eventmesh.protocol.tcp.EventMeshProtocolTCPServer;
import org.apache.eventmesh.protocol.tcp.model.DownStreamMsgContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MessageAckTask extends AbstractTask {

    private final Logger messageLogger = LoggerFactory.getLogger("message");

    public MessageAckTask(Package pkg, ChannelHandlerContext ctx, long startTime, EventMeshProtocolTCPServer eventMeshTCPServer) {
        super(pkg, ctx, startTime, eventMeshTCPServer);
    }

    @Override
    public void run() {
        long taskExecuteTime = System.currentTimeMillis();
        String seq = pkg.getHeader().getSeq();
        Command cmd = pkg.getHeader().getCommand();

        if (seq == null) {
            logger.error("MessageAckTask failed, seq cannot be null|user={}", session.getClient());
            return;
        }
        DownStreamMsgContext downStreamMsgContext = session.getPusher().getUnAckMsg().get(seq);
        if (downStreamMsgContext != null) {// ack non-broadcast msg
            downStreamMsgContext.ackMsg();
            session.getPusher().getUnAckMsg().remove(seq);
        } else {
            if (!cmd.equals(Command.RESPONSE_TO_CLIENT_ACK)) {
                logger.warn("MessageAckTask, seq:{}, downStreamMsgContext not in downStreamMap,client:{}", seq, session.getClient());
            }
        }
        messageLogger.info("pkg|c2eventMesh|cmd={}|seq=[{}]|user={}|wait={}ms|cost={}ms", cmd, seq, session.getClient(),
                taskExecuteTime - startTime, System.currentTimeMillis() - startTime);
    }
}
