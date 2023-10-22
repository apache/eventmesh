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

import org.apache.eventmesh.common.protocol.http.HttpCommand;
import org.apache.eventmesh.common.protocol.http.common.EventMeshRetCode;
import org.apache.eventmesh.common.protocol.http.common.RequestCode;
import org.apache.eventmesh.common.utils.IPUtils;
import org.apache.eventmesh.runtime.boot.EventMeshServer;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.protocol.http.async.AsyncContext;
import org.apache.eventmesh.runtime.core.protocol.http.processor.inf.HttpRequestProcessor;
import org.apache.eventmesh.runtime.util.RemotingHelper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.channel.ChannelHandlerContext;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class AdminShutdownProcessor implements HttpRequestProcessor {

    public final Logger cmdLogger = LoggerFactory.getLogger(EventMeshConstants.CMD);

    private final EventMeshServer eventMeshServer;

    @Override
    public void processRequest(ChannelHandlerContext ctx, AsyncContext<HttpCommand> asyncContext) throws Exception {

        String remoteAddr = RemotingHelper.parseChannelRemoteAddr(ctx.channel());
        cmdLogger.info("cmd={}|{}|client2eventMesh|from={}|to={}",
            RequestCode.get(Integer.valueOf(asyncContext.getRequest().getRequestCode())),
            EventMeshConstants.PROTOCOL_HTTP, remoteAddr, IPUtils.getLocalAddress());

        eventMeshServer.shutdown();

        HttpCommand responseEventMeshCommand = asyncContext.getRequest().createHttpCommandResponse(EventMeshRetCode.SUCCESS);
        asyncContext.onComplete(responseEventMeshCommand);
    }
}
