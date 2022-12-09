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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.net.httpserver.HttpExchange;

@SuppressWarnings("restriction")
@EventHttpHandler(path = "/webhook/updateWebHookConfig")
public class UpdateWebHookConfigHandler extends AbstractHttpHandler {

    public Logger logger = LoggerFactory.getLogger(this.getClass());


    private final WebHookConfigOperation operation;

    public UpdateWebHookConfigHandler(WebHookConfigOperation operation, HttpHandlerManager httpHandlerManager) {
        super(httpHandlerManager);
        this.operation = operation;
    }

    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
        NetUtils.sendSuccessResponseHeaders(httpExchange);

        // get requestBody and resolve to WebHookConfig
        String requestBody = NetUtils.parsePostBody(httpExchange);
        WebHookConfig webHookConfig = JsonUtils.deserialize(requestBody, WebHookConfig.class);

        try (OutputStream out = httpExchange.getResponseBody()) {
            Integer code = operation.updateWebHookConfig(webHookConfig); // operating result
            String result = 1 == code ? "updateWebHookConfig Succeed!" : "updateWebHookConfig Failed!";
            out.write(result.getBytes(Constants.DEFAULT_CHARSET));
        } catch (Exception e) {
            logger.error("get WebHookConfigOperation implementation Failed.", e);
        }
    }
}
