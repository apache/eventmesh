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

import java.util.Map;
import java.util.Objects;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpRequest;

import lombok.extern.slf4j.Slf4j;

/**
 * This class handles the HTTP requests of {@code /webhook/updateWebHookConfig} endpoint and updates an existing WebHook configuration according to
 * the given {@linkplain org.apache.eventmesh.webhook.api.WebHookConfig WebHookConfig}.
 * <p>
 * The implementation of {@linkplain org.apache.eventmesh.webhook.api.WebHookConfigOperation#updateWebHookConfig WebHookConfigOperation} interface
 * depends on the {@code eventMesh.webHook.operationMode} configuration in {@code eventmesh.properties}.
 * <p>
 * For example, when {@code eventMesh.webHook.operationMode=file}, It calls the
 * {@linkplain org.apache.eventmesh.webhook.admin.FileWebHookConfigOperation
 * #updateWebHookConfig
 * FileWebHookConfigOperation} method as implementation to update the WebHook configuration in a file;
 * <p>
 * When {@code eventMesh.webHook.operationMode=nacos}, It calls the
 * {@linkplain org.apache.eventmesh.webhook.admin.NacosWebHookConfigOperation#updateWebHookConfig
 * NacosWebHookConfigOperation} method as implementation to update the WebHook configuration in Nacos.
 * <p>
 * The {@linkplain org.apache.eventmesh.webhook.receive.storage.HookConfigOperationManager#updateWebHookConfig HookConfigOperationManager} , another
 * implementation of {@linkplain org.apache.eventmesh.webhook.api.WebHookConfigOperation WebHookConfigOperation} interface, is not used for this
 * endpoint.
 *
 * @see AbstractHttpHandler
 */

@SuppressWarnings("restriction")
@Slf4j
@EventMeshHttpHandler(path = "/webhook/updateWebHookConfig")
public class UpdateWebHookConfigHandler extends AbstractHttpHandler {

    private final WebHookConfigOperation operation;

    /**
     * Constructs a new instance with the specified WebHook config operation.
     *
     * @param operation the WebHookConfigOperation implementation used to update the WebHook config
     */
    public UpdateWebHookConfigHandler(WebHookConfigOperation operation) {
        super();
        this.operation = operation;
    }

    @Override
    public void handle(HttpRequest httpRequest, ChannelHandlerContext ctx) throws Exception {
        Map<String, Object> body = HttpRequestUtil.parseHttpRequestBody(httpRequest);
        Objects.requireNonNull(body, "body can not be null");
        // Resolve to WebHookConfig
        WebHookConfig webHookConfig = JsonUtils.mapToObject(body, WebHookConfig.class);
        // Update the existing WebHookConfig
        Integer code = operation.updateWebHookConfig(webHookConfig); // operating result
        String result = 1 == code ? "updateWebHookConfig Succeed!" : "updateWebHookConfig Failed!";
        writeText(ctx, result);
    }
}
