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

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import com.alibaba.nacos.api.config.ConfigFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.exception.NacosException;

import lombok.extern.slf4j.Slf4j;

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
     * Initialize according to operationMode
     *
     * @param receiveConfiguration receiveConfiguration
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

    @Override
    public List<WebHookConfig> queryWebHookConfigByManufacturer(final WebHookConfig webHookConfig,
        final Integer pageNum,
        final Integer pageSize) {
        return new ArrayList<WebHookConfig>();
    }

    @Override
    public Integer insertWebHookConfig(final WebHookConfig webHookConfig) {
        cacheWebHookConfig.put(webHookConfig.getCallbackPath(), webHookConfig);
        return 1;
    }

    @Override
    public Integer updateWebHookConfig(final WebHookConfig webHookConfig) {
        cacheWebHookConfig.put(webHookConfig.getCallbackPath(), webHookConfig);
        return 1;
    }

    @Override
    public Integer deleteWebHookConfig(final WebHookConfig webHookConfig) {
        cacheWebHookConfig.remove(webHookConfig.getCallbackPath());
        return 1;
    }

}
