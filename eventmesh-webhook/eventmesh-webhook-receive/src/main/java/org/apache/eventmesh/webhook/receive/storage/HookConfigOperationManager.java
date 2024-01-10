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

package org.apache.eventmesh.webhook.receive.storage;

import static org.apache.eventmesh.webhook.api.WebHookOperationConstant.DATA_ID_EXTENSION;
import static org.apache.eventmesh.webhook.api.WebHookOperationConstant.GROUP_PREFIX;
import static org.apache.eventmesh.webhook.api.WebHookOperationConstant.OPERATION_MODE_FILE;
import static org.apache.eventmesh.webhook.api.WebHookOperationConstant.OPERATION_MODE_NACOS;
import static org.apache.eventmesh.webhook.api.WebHookOperationConstant.TIMEOUT_MS;

import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.webhook.api.WebHookConfig;
import org.apache.eventmesh.webhook.api.WebHookConfigOperation;
import org.apache.eventmesh.webhook.api.utils.ClassUtils;
import org.apache.eventmesh.webhook.receive.config.ReceiveConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import com.alibaba.nacos.api.config.ConfigFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.exception.NacosException;

import lombok.extern.slf4j.Slf4j;

/**
 * This class manages the operations related to WebHook configurations. Mainly used by
 * {@linkplain org.apache.eventmesh.webhook.receive.WebHookController#execute WebHookController}
 * to retrieve existing WebHook configuration by callback path when processing received WebHook data from manufacturers.
 * <p>
 * This class is initialized together with the {@linkplain org.apache.eventmesh.webhook.receive.WebHookController WebHookController}
 * during the initialization phase of the {@code EventMeshHTTPServer}.
 *
 * @see WebHookConfigOperation
 */

@Slf4j
public class HookConfigOperationManager implements WebHookConfigOperation {

    /**
     * webhook config pool -> key is CallbackPath
     */
    private final transient Map<String, WebHookConfig> cacheWebHookConfig = new ConcurrentHashMap<>();
    private transient String operationMode;
    private transient ConfigService nacosConfigService;

    public HookConfigOperationManager() {
    }

    /**
     * Initialize according to operation mode.
     * <p>
     * OPERATION_MODE_FILE: The WebHook configurations are read from a file specified by the filePath.
     * OPERATION_MODE_NACOS: The WebHook configurations are fetched from a Nacos configuration service
     *                       using the properties specified in operationProperties.
     *
     * @param receiveConfiguration The ReceiveConfiguration object containing the operation mode and related properties.
     * @throws NacosException        If there is an error with the Nacos configuration service.
     */
    public HookConfigOperationManager(final ReceiveConfiguration receiveConfiguration) throws NacosException {

        this.operationMode = receiveConfiguration.getOperationMode();

        switch (operationMode) {
            case OPERATION_MODE_FILE:
                new WebhookFileListener(receiveConfiguration.getFilePath(), cacheWebHookConfig);
                break;
            case OPERATION_MODE_NACOS:
                nacosModeInit(receiveConfiguration.getOperationProperties());
                break;
            default:
                break;
        }
    }

    private void nacosModeInit(final Properties config) throws NacosException {
        nacosConfigService = ConfigFactory.createConfigService(config);
    }

    /**
     * Retrieves a WebHook configuration according to its WebHook callback path in
     * {@linkplain org.apache.eventmesh.webhook.api.WebHookConfig WebHookConfig}.
     *
     * @param webHookConfig The WebHookConfig object containing the callback path.
     * @return The retrieved WebHookConfig object which contains full configuration.
     */
    @Override
    public WebHookConfig queryWebHookConfigById(final WebHookConfig webHookConfig) {
        switch (operationMode) {
            case OPERATION_MODE_FILE:
                return cacheWebHookConfig.get(ClassUtils.convertResourcePathToClassName(webHookConfig.getCallbackPath()));
            case OPERATION_MODE_NACOS:
                try {
                    final String content = nacosConfigService.getConfig(
                        webHookConfig.getManufacturerEventName() + DATA_ID_EXTENSION,
                        GROUP_PREFIX + webHookConfig.getManufacturerName(),
                        TIMEOUT_MS);
                    return JsonUtils.parseObject(content, WebHookConfig.class);
                } catch (NacosException e) {
                    log.error("queryWebHookConfigById failed", e);
                }
                break;
            default:
                break;
        }
        return null;
    }

    /**
     * Retrieves a list of WebHook configurations for a specific manufacturer.
     *
     * @param webHookConfig The WebHookConfig object containing the manufacturer name.
     * @param pageNum       The page number for pagination.
     * @param pageSize      The page size for pagination.
     * @return The list of WebHookConfig objects which each contains full configuration.
     */
    @Override
    public List<WebHookConfig> queryWebHookConfigByManufacturer(final WebHookConfig webHookConfig,
        final Integer pageNum,
        final Integer pageSize) {
        return new ArrayList<WebHookConfig>();
    }

    /**
     * Inserts a new WebHook configuration into the cache.
     *
     * @param webHookConfig The WebHookConfig object to insert.
     * @return The result code indicating the success of the operation.
     */
    @Override
    public Integer insertWebHookConfig(final WebHookConfig webHookConfig) {
        cacheWebHookConfig.put(webHookConfig.getCallbackPath(), webHookConfig);
        return 1;
    }

    /**
     * Updates an existing WebHook configuration in the cache.
     *
     * @param webHookConfig The WebHookConfig object to update.
     * @return The result code indicating the success of the operation.
     */
    @Override
    public Integer updateWebHookConfig(final WebHookConfig webHookConfig) {
        cacheWebHookConfig.put(webHookConfig.getCallbackPath(), webHookConfig);
        return 1;
    }

    /**
     * Deletes a WebHook configuration from the cache.
     *
     * @param webHookConfig The WebHookConfig object to delete.
     * @return The result code indicating the success of the operation.
     */
    @Override
    public Integer deleteWebHookConfig(final WebHookConfig webHookConfig) {
        cacheWebHookConfig.remove(webHookConfig.getCallbackPath());
        return 1;
    }

}
