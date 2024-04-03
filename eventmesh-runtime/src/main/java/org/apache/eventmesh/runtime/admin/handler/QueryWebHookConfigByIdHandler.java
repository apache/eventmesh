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

package org.apache.eventmesh.runtime.admin.handler;

import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.runtime.common.EventHttpHandler;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.util.HttpResponseUtils;
import org.apache.eventmesh.webhook.api.WebHookConfig;
import org.apache.eventmesh.webhook.api.WebHookConfigOperation;

import java.util.Map;
import java.util.Objects;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;

import lombok.extern.slf4j.Slf4j;

/**
 * This class handles the HTTP requests of {@code /webhook/queryWebHookConfigById} endpoint and returns the corresponding WebHook configuration
 * information based on the WebHook callback path specified in {@linkplain org.apache.eventmesh.webhook.api.WebHookConfig WebHookConfig}.
 * <p>
 * Parameters:
 * <ul>
 *     <li>WebHook callback path: {@code callbackPath} | Example: {@code /webhook/github/eventmesh/all}</li>
 *     <li>WebHook manufacturer name: {@code manufacturerName} | Example: {@code github}</li>
 * </ul>
 * The implementation of
 * {@linkplain org.apache.eventmesh.webhook.api.WebHookConfigOperation#queryWebHookConfigById WebHookConfigOperation}
 * interface depends on the {@code eventMesh.webHook.operationMode} configuration in {@code eventmesh.properties}.
 * <p>
 * For example, when {@code eventMesh.webHook.operationMode=file}, It calls the
 * {@linkplain org.apache.eventmesh.webhook.admin.FileWebHookConfigOperation#queryWebHookConfigById FileWebHookConfigOperation}
 * method as implementation to retrieve the WebHook configuration from a file;
 * <p>
 * When {@code eventMesh.webHook.operationMode=nacos}, It calls the
 * {@linkplain org.apache.eventmesh.webhook.admin.NacosWebHookConfigOperation#queryWebHookConfigById NacosWebHookConfigOperation}
 * method as implementation to retrieve the WebHook configuration from Nacos.
 * <p>
 * After this, the {@linkplain org.apache.eventmesh.webhook.receive.WebHookController#execute WebHookController}
 * will use
 * {@linkplain org.apache.eventmesh.webhook.receive.storage.HookConfigOperationManager#queryWebHookConfigById HookConfigOperationManager}
 * to retrieve existing WebHook configuration by callback path when processing received WebHook data from manufacturers.
 *
 * @see AbstractHttpHandler
 */

@SuppressWarnings("restriction")
@Slf4j
@EventHttpHandler(path = "/webhook/queryWebHookConfigById")
public class QueryWebHookConfigByIdHandler extends AbstractHttpHandler {

    private final WebHookConfigOperation operation;

    /**
     * Constructs a new instance with the specified WebHook config operation.
     *
     * @param operation the WebHookConfigOperation implementation used to query the WebHook config
     */
    public QueryWebHookConfigByIdHandler(WebHookConfigOperation operation) {
        super();
        this.operation = operation;
    }

    @Override
    public void handle(HttpRequest httpRequest, ChannelHandlerContext ctx) throws Exception {
        HttpHeaders responseHeaders = new DefaultHttpHeaders();
        responseHeaders.add(EventMeshConstants.CONTENT_TYPE, EventMeshConstants.APPLICATION_JSON);
        responseHeaders.add(EventMeshConstants.HANDLER_ORIGIN, "*");
        // Resolve to WebHookConfig
        Map<String, Object> body = parseHttpRequestBody(httpRequest);
        if (!Objects.isNull(body)) {
            WebHookConfig webHookConfig = JsonUtils.mapToObject(body, WebHookConfig.class);
            // Retrieve the WebHookConfig by callback path
            WebHookConfig result = operation.queryWebHookConfigById(webHookConfig); // operating result
            String json = JsonUtils.toJSONString(result);
            HttpResponse httpResponse =
                HttpResponseUtils.getHttpResponse(Objects.requireNonNull(json).getBytes(Constants.DEFAULT_CHARSET), ctx, responseHeaders,
                    HttpResponseStatus.OK);
            write(ctx, httpResponse);
        }
        throw new Exception();
    }
}
