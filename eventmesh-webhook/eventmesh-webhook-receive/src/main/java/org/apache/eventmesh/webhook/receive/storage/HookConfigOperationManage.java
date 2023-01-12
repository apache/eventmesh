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
import org.apache.eventmesh.webhook.api.utils.StringUtils;
import org.apache.eventmesh.webhook.receive.config.ReceiveConfiguration;

import java.io.FileNotFoundException;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.nacos.api.config.ConfigFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.exception.NacosException;

public class HookConfigOperationManage implements WebHookConfigOperation {
    /**
     * webhook config pool -> key is CallbackPath
     */
    private final Map<String, WebHookConfig> cacheWebHookConfig = new ConcurrentHashMap<>();
    public Logger logger = LoggerFactory.getLogger(this.getClass());
    private String operationMode;
    private ConfigService nacosConfigService;

    public HookConfigOperationManage() {
    }

    /**
     * Initialize according to operationMode
     *
     * @param receiveConfiguration receiveConfiguration
     */
    public HookConfigOperationManage(ReceiveConfiguration receiveConfiguration) throws FileNotFoundException, NacosException {

        this.operationMode = receiveConfiguration.getOperationMode();

        if (OPERATION_MODE_FILE.equals(operationMode)) {
            new WebhookFileListener(receiveConfiguration.getFilePath(), cacheWebHookConfig);
        } else if (OPERATION_MODE_NACOS.equals(operationMode)) {
            nacosModeInit(receiveConfiguration.getOperationProperties());
        }
    }

    private void nacosModeInit(Properties config) throws NacosException {
        nacosConfigService = ConfigFactory.createConfigService(config);
    }

    @Override
    public WebHookConfig queryWebHookConfigById(WebHookConfig webHookConfig) {
        if (OPERATION_MODE_FILE.equals(operationMode)) {
            return cacheWebHookConfig.get(StringUtils.getFileName(webHookConfig.getCallbackPath()));
        } else if (OPERATION_MODE_NACOS.equals(operationMode)) {
            try {
                String content = nacosConfigService.getConfig(webHookConfig.getManufacturerEventName() + DATA_ID_EXTENSION,
                    GROUP_PREFIX + webHookConfig.getManufacturerName(), TIMEOUT_MS);
                return JsonUtils.deserialize(content, WebHookConfig.class);
            } catch (NacosException e) {
                logger.error("queryWebHookConfigById failed", e);
            }
        }
        return null;
    }

    @Override
    public List<WebHookConfig> queryWebHookConfigByManufacturer(WebHookConfig webHookConfig, Integer pageNum,
                                                                Integer pageSize) {
        return null;
    }

    @Override
    public Integer insertWebHookConfig(WebHookConfig webHookConfig) {
        cacheWebHookConfig.put(webHookConfig.getCallbackPath(), webHookConfig);
        return 1;
    }

    @Override
    public Integer updateWebHookConfig(WebHookConfig webHookConfig) {
        cacheWebHookConfig.put(webHookConfig.getCallbackPath(), webHookConfig);
        return 1;
    }

    @Override
    public Integer deleteWebHookConfig(WebHookConfig webHookConfig) {
        cacheWebHookConfig.remove(webHookConfig.getCallbackPath());
        return 1;
    }

}
