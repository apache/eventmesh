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

@SuppressWarnings("restriction")
@Slf4j
@EventHttpHandler(path = "/webhook/queryWebHookConfigByManufacturer")
public class QueryWebHookConfigByManufacturerHandler extends AbstractHttpHandler {

    private final transient WebHookConfigOperation operation;

    public QueryWebHookConfigByManufacturerHandler(WebHookConfigOperation operation,
        HttpHandlerManager httpHandlerManager) {
        super(httpHandlerManager);
        this.operation = operation;
        Objects.requireNonNull(operation, "WebHookConfigOperation can not be null");
        Objects.requireNonNull(httpHandlerManager, "HttpHandlerManager can not be null");

    }


    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
        Objects.requireNonNull(httpExchange, "httpExchange can not be null");

        httpExchange.getResponseHeaders().add(CONTENT_TYPE, APPLICATION_JSON);
        NetUtils.sendSuccessResponseHeaders(httpExchange);

        // get requestBody and resolve to WebHookConfig
        JsonNode node = JsonUtils.getJsonNode(NetUtils.parsePostBody(httpExchange));
        Objects.requireNonNull(node, "JsonNode can not be null");

        WebHookConfig webHookConfig = JsonUtils.parseObject(node.get("webHookConfig").toString(), WebHookConfig.class);
        Integer pageNum = Integer.valueOf(node.get("pageNum").toString());
        Integer pageSize = Integer.valueOf(node.get("pageSize").toString());

        try (OutputStream out = httpExchange.getResponseBody()) {
            List<WebHookConfig> result = operation.queryWebHookConfigByManufacturer(webHookConfig, pageNum, pageSize); // operating result
            out.write(Objects.requireNonNull(JsonUtils.toJSONString(result)).getBytes(Constants.DEFAULT_CHARSET));
        } catch (Exception e) {
            log.error("get WebHookConfigOperation implementation Failed.", e);
        }
    }
}
