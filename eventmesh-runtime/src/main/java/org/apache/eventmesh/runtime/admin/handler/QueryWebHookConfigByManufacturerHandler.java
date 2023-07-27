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
import org.apache.eventmesh.runtime.admin.controller.HttpHandlerManager;
import org.apache.eventmesh.runtime.common.EventHttpHandler;
import org.apache.eventmesh.webhook.api.WebHookConfig;
import org.apache.eventmesh.webhook.api.WebHookConfigOperation;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Objects;


import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpExchange;

import lombok.extern.slf4j.Slf4j;

/**
 * This class handles the HTTP requests of {@code /webhook/queryWebHookConfigByManufacturer} endpoint
 * and returns a list of WebHook configurations
 * based on the WebHook manufacturer name (such as github) specified in {@linkplain org.apache.eventmesh.webhook.api.WebHookConfig WebHookConfig}.
 * <p>
 * The implementation of
 * {@linkplain org.apache.eventmesh.webhook.api.WebHookConfigOperation#queryWebHookConfigByManufacturer WebHookConfigOperation}
 * interface depends on the {@code eventMesh.webHook.operationMode} configuration in {@code eventmesh.properties}.
 * <p>
 * For example, when {@code eventMesh.webHook.operationMode=file}, It calls the
 * {@linkplain org.apache.eventmesh.webhook.admin.FileWebHookConfigOperation#queryWebHookConfigByManufacturer FileWebHookConfigOperation}
 * method as implementation to retrieve the WebHook configuration from a file;
 * <p>
 * When {@code eventMesh.webHook.operationMode=nacos}, It calls the
 * {@linkplain org.apache.eventmesh.webhook.admin.NacosWebHookConfigOperation#queryWebHookConfigByManufacturer NacosWebHookConfigOperation}
 * method as implementation to retrieve the WebHook configuration from Nacos.
 * <p>
 * The
 * {@linkplain org.apache.eventmesh.webhook.receive.storage.HookConfigOperationManager#queryWebHookConfigByManufacturer HookConfigOperationManager}
 * , another implementation of {@linkplain org.apache.eventmesh.webhook.api.WebHookConfigOperation WebHookConfigOperation}
 * interface, is not used for this endpoint.
 *
 * @see AbstractHttpHandler
 */

@SuppressWarnings("restriction")
@Slf4j
@EventHttpHandler(path = "/webhook/queryWebHookConfigByManufacturer")
public class QueryWebHookConfigByManufacturerHandler extends AbstractHttpHandler {

    private final transient WebHookConfigOperation operation;

    /**
     * Constructs a new instance with the specified WebHook config operation and HTTP handler manager.
     *
     * @param operation the WebHookConfigOperation implementation used to query the WebHook config
     * @param httpHandlerManager Manages the registration of {@linkplain com.sun.net.httpserver.HttpHandler HttpHandler}
     *                           for an {@link com.sun.net.httpserver.HttpServer HttpServer}.
     */
    public QueryWebHookConfigByManufacturerHandler(WebHookConfigOperation operation,
        HttpHandlerManager httpHandlerManager) {
        super(httpHandlerManager);
        this.operation = operation;
        Objects.requireNonNull(operation, "WebHookConfigOperation can not be null");
        Objects.requireNonNull(httpHandlerManager, "HttpHandlerManager can not be null");

    }

    /**
     * Handles requests by retrieving a list of WebHook configurations.
     *
     * @param httpExchange the exchange containing the request from the client and used to send the response
     * @throws IOException if an I/O error occurs while handling the request
     */
    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
        Objects.requireNonNull(httpExchange, "httpExchange can not be null");

        httpExchange.getResponseHeaders().add(CONTENT_TYPE, APPLICATION_JSON);
        NetUtils.sendSuccessResponseHeaders(httpExchange);

        // Resolve to WebHookConfig
        JsonNode node = JsonUtils.getJsonNode(NetUtils.parsePostBody(httpExchange));
        Objects.requireNonNull(node, "JsonNode can not be null");

        WebHookConfig webHookConfig = JsonUtils.parseObject(node.get("webHookConfig").toString(), WebHookConfig.class);
        Integer pageNum = Integer.valueOf(node.get("pageNum").toString());
        Integer pageSize = Integer.valueOf(node.get("pageSize").toString());

        try (OutputStream out = httpExchange.getResponseBody()) {
            // Retrieve the WebHookConfig list by manufacturer name
            List<WebHookConfig> result = operation.queryWebHookConfigByManufacturer(webHookConfig, pageNum, pageSize); // operating result
            out.write(Objects.requireNonNull(JsonUtils.toJSONString(result)).getBytes(Constants.DEFAULT_CHARSET));
        } catch (Exception e) {
            log.error("get WebHookConfigOperation implementation Failed.", e);
        }
    }
}
