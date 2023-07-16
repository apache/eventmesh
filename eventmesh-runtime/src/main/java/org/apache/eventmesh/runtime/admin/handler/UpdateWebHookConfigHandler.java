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
import org.apache.eventmesh.common.utils.NetUtils;
import org.apache.eventmesh.runtime.admin.controller.HttpHandlerManager;
import org.apache.eventmesh.runtime.common.EventHttpHandler;
import org.apache.eventmesh.webhook.api.WebHookConfig;
import org.apache.eventmesh.webhook.api.WebHookConfigOperation;

import java.io.IOException;
import java.io.OutputStream;


import com.sun.net.httpserver.HttpExchange;

import lombok.extern.slf4j.Slf4j;

/**
 * This class handles the HTTP requests of {@code /webhook/updateWebHookConfig} endpoint
 * and updates an existing WebHook configuration
 * according to the given {@linkplain org.apache.eventmesh.webhook.api.WebHookConfig WebHookConfig}.
 * <p>
 * The implementation of
 * {@linkplain org.apache.eventmesh.webhook.api.WebHookConfigOperation#updateWebHookConfig WebHookConfigOperation}
 * interface depends on the {@code eventMesh.webHook.operationMode} configuration in {@code eventmesh.properties}.
 * <p>
 * For example, when {@code eventMesh.webHook.operationMode=file}, It calls the
 * {@linkplain org.apache.eventmesh.webhook.admin.FileWebHookConfigOperation#updateWebHookConfig FileWebHookConfigOperation}
 * method as implementation to update the WebHook configuration in a file;
 * <p>
 * When {@code eventMesh.webHook.operationMode=nacos}, It calls the
 * {@linkplain org.apache.eventmesh.webhook.admin.NacosWebHookConfigOperation#updateWebHookConfig NacosWebHookConfigOperation}
 * method as implementation to update the WebHook configuration in Nacos.
 * <p>
 * The {@linkplain org.apache.eventmesh.webhook.receive.storage.HookConfigOperationManager#updateWebHookConfig HookConfigOperationManager}
 * which implements the {@linkplain org.apache.eventmesh.webhook.api.WebHookConfigOperation WebHookConfigOperation}
 * interface, does not participate in the implementation of this endpoint.
 *
 * @see AbstractHttpHandler
 */

@SuppressWarnings("restriction")
@Slf4j
@EventHttpHandler(path = "/webhook/updateWebHookConfig")
public class UpdateWebHookConfigHandler extends AbstractHttpHandler {

    private final WebHookConfigOperation operation;

    /**
     * Constructs a new instance with the specified WebHook config operation and HTTP handler manager.
     *
     * @param operation the WebHookConfigOperation implementation used to update the WebHook config
     * @param httpHandlerManager Manages the registration of {@linkplain com.sun.net.httpserver.HttpHandler HttpHandler}
     *                           for an {@link com.sun.net.httpserver.HttpServer HttpServer}.
     */
    public UpdateWebHookConfigHandler(WebHookConfigOperation operation, HttpHandlerManager httpHandlerManager) {
        super(httpHandlerManager);
        this.operation = operation;
    }

    /**
     * Handles the HTTP requests by updating a WebHook configuration.
     * <p>
     * This method is an implementation of {@linkplain com.sun.net.httpserver.HttpHandler#handle(HttpExchange)  HttpHandler.handle()}.
     *
     * @param httpExchange the exchange containing the request from the client and used to send the response
     * @throws IOException if an I/O error occurs while handling the request
     *
     * @see org.apache.eventmesh.webhook.receive.storage.HookConfigOperationManager#updateWebHookConfig(WebHookConfig)
     */
    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
        NetUtils.sendSuccessResponseHeaders(httpExchange);

        // get requestBody and resolve to WebHookConfig
        String requestBody = NetUtils.parsePostBody(httpExchange);
        WebHookConfig webHookConfig = JsonUtils.parseObject(requestBody, WebHookConfig.class);

        try (OutputStream out = httpExchange.getResponseBody()) {
            // Update the existing WebHookConfig and get the operation result code
            Integer code = operation.updateWebHookConfig(webHookConfig); // operating result
            String result = 1 == code ? "updateWebHookConfig Succeed!" : "updateWebHookConfig Failed!";
            out.write(result.getBytes(Constants.DEFAULT_CHARSET));
        } catch (Exception e) {
            log.error("get WebHookConfigOperation implementation Failed.", e);
        }
    }
}
