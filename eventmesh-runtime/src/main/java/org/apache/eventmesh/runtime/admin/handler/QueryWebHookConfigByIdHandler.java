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

import static org.apache.eventmesh.runtime.constants.EventMeshConstants.APPLICATION_JSON;
import static org.apache.eventmesh.runtime.constants.EventMeshConstants.CONTENT_TYPE;

import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.common.utils.NetUtils;
import org.apache.eventmesh.runtime.common.EventHttpHandler;
import org.apache.eventmesh.webhook.api.WebHookConfig;
import org.apache.eventmesh.webhook.api.WebHookConfigOperation;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;

import com.sun.net.httpserver.HttpExchange;

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
     * Constructs a new instance with the specified WebHook config operation and HTTP handler manager.
     *
     * @param operation the WebHookConfigOperation implementation used to query the WebHook config
     */
    public QueryWebHookConfigByIdHandler(WebHookConfigOperation operation) {
        super();
        this.operation = operation;
    }

    /**
     * Handles requests by retrieving a WebHook configuration.
     *
     * @param httpExchange the exchange containing the request from the client and used to send the response
     * @throws IOException if an I/O error occurs while handling the request
     */
    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
        httpExchange.getResponseHeaders().add(CONTENT_TYPE, APPLICATION_JSON);
        NetUtils.sendSuccessResponseHeaders(httpExchange);

        // Resolve to WebHookConfig
        String requestBody = NetUtils.parsePostBody(httpExchange);
        WebHookConfig webHookConfig = JsonUtils.parseObject(requestBody, WebHookConfig.class);

        try (OutputStream out = httpExchange.getResponseBody()) {
            // Retrieve the WebHookConfig by callback path
            WebHookConfig result = operation.queryWebHookConfigById(webHookConfig); // operating result
            out.write(Objects.requireNonNull(JsonUtils.toJSONString(result)).getBytes(Constants.DEFAULT_CHARSET));
        } catch (Exception e) {
            log.error("get WebHookConfigOperation implementation Failed.", e);
        }
    }
}
