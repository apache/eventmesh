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

package org.apache.eventmesh.runtime.admin.handler.v1;

import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.runtime.admin.handler.AbstractHttpHandler;
import org.apache.eventmesh.runtime.common.EventMeshHttpHandler;
import org.apache.eventmesh.runtime.util.HttpRequestUtil;
import org.apache.eventmesh.webhook.api.WebHookConfig;
import org.apache.eventmesh.webhook.api.WebHookConfigOperation;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpRequest;

import lombok.extern.slf4j.Slf4j;

/**
 * This class handles the HTTP requests of {@code /webhook/queryWebHookConfigByManufacturer} endpoint and returns a list of WebHook configurations
 * based on the WebHook manufacturer name (such as github) specified in {@linkplain org.apache.eventmesh.webhook.api.WebHookConfig WebHookConfig}.
 * <p>
 * The implementation of {@linkplain org.apache.eventmesh.webhook.api.WebHookConfigOperation#queryWebHookConfigByManufacturer WebHookConfigOperation}
 * interface depends on the {@code eventMesh.webHook.operationMode} configuration in {@code eventmesh.properties}.
 * <p>
 * For example, when {@code eventMesh.webHook.operationMode=file} It calls the
 * {@linkplain org.apache.eventmesh.webhook.admin.FileWebHookConfigOperation
 * #queryWebHookConfigByManufacturer
 * FileWebHookConfigOperation} method as implementation to retrieve the WebHook configuration from a file;
 * <p>
 * When {@code eventMesh.webHook.operationMode=nacos} It calls the {@linkplain org.apache.eventmesh.webhook.admin.NacosWebHookConfigOperation
 * #queryWebHookConfigByManufacturer
 * NacosWebHookConfigOperation} method as implementation to retrieve the WebHook configuration from Nacos.
 * <p>
 * The {@linkplain org.apache.eventmesh.webhook.receive.storage.HookConfigOperationManager#queryWebHookConfigByManufacturer
 * HookConfigOperationManager} , another implementation of {@linkplain org.apache.eventmesh.webhook.api.WebHookConfigOperation WebHookConfigOperation}
 * interface, is not used for this endpoint.
 *
 * @see AbstractHttpHandler
 */

@SuppressWarnings("restriction")
@Slf4j
@EventMeshHttpHandler(path = "/webhook/queryWebHookConfigByManufacturer")
public class QueryWebHookConfigByManufacturerHandler extends AbstractHttpHandler {

    private final transient WebHookConfigOperation operation;

    /**
     * Constructs a new instance with the specified WebHook config operation.
     *
     * @param operation the WebHookConfigOperation implementation used to query the WebHook config
     */
    public QueryWebHookConfigByManufacturerHandler(WebHookConfigOperation operation) {
        super();
        this.operation = operation;
        Objects.requireNonNull(operation, "WebHookConfigOperation can not be null");

    }

    @Override
    public void handle(HttpRequest httpRequest, ChannelHandlerContext ctx) throws Exception {
        // Resolve to WebHookConfig
        Map<String, Object> body = HttpRequestUtil.parseHttpRequestBody(httpRequest);
        Objects.requireNonNull(body, "body can not be null");
        WebHookConfig webHookConfig = JsonUtils.mapToObject(body, WebHookConfig.class);
        Integer pageNum = Integer.valueOf(body.get("pageNum").toString());
        Integer pageSize = Integer.valueOf(body.get("pageSize").toString());

        // Retrieve the WebHookConfig list by manufacturer name
        List<WebHookConfig> listWebHookConfig = operation.queryWebHookConfigByManufacturer(webHookConfig, pageNum, pageSize); // operating result
        String result = JsonUtils.toJSONString(listWebHookConfig);
        writeJson(ctx, result);
    }
}
