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

import org.apache.eventmesh.admin.rocketmq.util.JsonUtils;
import org.apache.eventmesh.admin.rocketmq.util.NetUtils;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.webhook.api.WebHookConfig;
import org.apache.eventmesh.webhook.api.WebHookConfigOperation;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

@SuppressWarnings("restriction")
public class QueryWebHookConfigByManufacturerHandler implements HttpHandler {

    public Logger logger = LoggerFactory.getLogger(this.getClass());

    private WebHookConfigOperation operation;

    public QueryWebHookConfigByManufacturerHandler(WebHookConfigOperation operation) {
        this.operation = operation;
    }


    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
        httpExchange.sendResponseHeaders(200, 0);
        httpExchange.getResponseHeaders().add("Content-Type", "application/json");

        // get requestBody and resolve to WebHookConfig
        String requestBody = NetUtils.parsePostBody(httpExchange);
        JsonNode node = JsonUtils.getJsonNode(requestBody);
        WebHookConfig webHookConfig = JsonUtils.toObject(node.get("webHookConfig").toString(), WebHookConfig.class);
        Integer pageNum = Integer.parseInt(node.get("pageNum").toString());
        Integer pageSize = Integer.parseInt(node.get("pageSize").toString());

        try (OutputStream out = httpExchange.getResponseBody()) {
            List<WebHookConfig> result = operation.queryWebHookConfigByManufacturer(webHookConfig, pageNum, pageSize); // operating result
            out.write(JsonUtils.toJson(result).getBytes(Constants.DEFAULT_CHARSET));
        } catch (Exception e) {
            logger.error("get WebHookConfigOperation implementation Failed.", e);
        }
    }
}
