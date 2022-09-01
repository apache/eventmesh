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

import java.io.IOException;
import java.io.OutputStream;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.apache.eventmesh.admin.rocketmq.util.JsonUtils;
import org.apache.eventmesh.admin.rocketmq.util.NetUtils;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.webhook.api.WebHookConfig;
import org.apache.eventmesh.webhook.api.WebHookConfigOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.eventmesh.admin.rocketmq.Constants.CONTENT_TYPE;
import static org.apache.eventmesh.admin.rocketmq.Constants.APPLICATION_JSON;

@SuppressWarnings("restriction")
public class QueryWebHookConfigByIdHandler implements HttpHandler {

    public Logger logger = LoggerFactory.getLogger(this.getClass());

    private WebHookConfigOperation operation;

    public QueryWebHookConfigByIdHandler(WebHookConfigOperation operation) {
        this.operation = operation;
    }


    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
        httpExchange.sendResponseHeaders(200, 0);
        httpExchange.getResponseHeaders().add(CONTENT_TYPE, APPLICATION_JSON);

        // get requestBody and resolve to WebHookConfig
        String requestBody = NetUtils.parsePostBody(httpExchange);
        WebHookConfig webHookConfig = JsonUtils.toObject(requestBody, WebHookConfig.class);

        try (OutputStream out = httpExchange.getResponseBody()) {
            WebHookConfig result = operation.queryWebHookConfigById(webHookConfig); // operating result
            out.write(JsonUtils.toJson(result).getBytes(Constants.DEFAULT_CHARSET));
        } catch (Exception e) {
            logger.error("get WebHookConfigOperation implementation Failed.", e);
        }
    }
}
