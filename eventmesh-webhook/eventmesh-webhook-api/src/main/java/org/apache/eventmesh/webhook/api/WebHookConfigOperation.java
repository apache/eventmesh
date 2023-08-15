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

package org.apache.eventmesh.webhook.api;

import java.util.List;

/**
 * This interface has three implementation classes, among which
 * {@code FileWebHookConfigOperation} and {@code NacosWebHookConfigOperation}
 * serve the {@code /webhook/deleteWebHookConfig} endpoint.
 * <p>
 * They correspond to the persistent configuration of {@linkplain org.apache.eventmesh.webhook.api.WebHookConfig WebHookConfig}
 * for {@code file} and {@code Nacos}, respectively.
 * <p>
 * However, the {@code HookConfigOperationManager}, which is located in the {@code org.apache.eventmesh.webhook.receive.storage}
 * package, differs from the other two implementations which are located in the {@code org.apache.eventmesh.webhook.admin}
 * package. Refer to {@code QueryWebHookConfigByIdHandler} for the reasons and details.
 */
public interface WebHookConfigOperation {

    Integer insertWebHookConfig(WebHookConfig webHookConfig);

    Integer updateWebHookConfig(WebHookConfig webHookConfig);

    Integer deleteWebHookConfig(WebHookConfig webHookConfig);

    WebHookConfig queryWebHookConfigById(WebHookConfig webHookConfig);

    List<WebHookConfig> queryWebHookConfigByManufacturer(WebHookConfig webHookConfig, Integer pageNum,
        Integer pageSize);
}
