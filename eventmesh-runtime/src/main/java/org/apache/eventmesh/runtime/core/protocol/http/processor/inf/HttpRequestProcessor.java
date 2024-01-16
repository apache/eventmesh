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

package org.apache.eventmesh.runtime.core.protocol.http.processor.inf;

import org.apache.eventmesh.common.protocol.http.HttpCommand;
import org.apache.eventmesh.common.protocol.http.body.Body;
import org.apache.eventmesh.common.protocol.http.common.EventMeshRetCode;
import org.apache.eventmesh.common.protocol.http.header.Header;
import org.apache.eventmesh.runtime.core.protocol.http.async.AsyncContext;

import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.cloudevents.CloudEvent;
import io.netty.channel.ChannelHandlerContext;

/**
 * HttpRequestProcessor
 */
public interface HttpRequestProcessor {

    Logger log = LoggerFactory.getLogger(HttpRequestProcessor.class);

    void processRequest(final ChannelHandlerContext ctx, final AsyncContext<HttpCommand> asyncContext)
        throws Exception;

    default boolean rejectRequest() {
        return false;
    }

    default <T extends Header, E extends Body> void completeResponse(HttpCommand req, AsyncContext<HttpCommand> asyncContext,
        T respHeader, EventMeshRetCode emCode,
        String msg, Class<E> clazz) {
        try {
            Method method = clazz.getMethod("buildBody", Integer.class, String.class);
            Object o = method.invoke(null, emCode.getRetCode(),
                StringUtils.isNotBlank(msg) ? msg : emCode.getErrMsg());
            HttpCommand response = req.createHttpCommandResponse(respHeader, (Body) o);
            asyncContext.onComplete(response);
        } catch (Exception e) {
            log.error("response failed", e);
        }
    }

    default String getExtension(CloudEvent event, String protocolKey) {
        Object extension = event.getExtension(protocolKey);
        return Objects.isNull(extension) ? "" : extension.toString();
    }

    /**
     * @return {@link Executor}
     */
    Executor executor();

}
