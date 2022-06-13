package org.apache.eventmesh.webhook.admin;

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

import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.webhook.api.ManufacturerObject;
import org.apache.eventmesh.webhook.api.WebHookConfig;
import org.apache.eventmesh.webhook.api.WebHookConfigOperation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.config.ConfigFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.ConfigType;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.shaded.io.grpc.netty.shaded.io.netty.util.internal.StringUtil;

public class NacosWebHookConfigOperation implements WebHookConfigOperation {

    private static final Logger logger = LoggerFactory.getLogger(NacosWebHookConfigOperation.class);

    private static final String GROUP_PREFIX = "webhook_";

    private static final String DATA_ID_EXTENSION = ".json";

    private static final Integer TIMEOUT_MS = 3 * 1000;

    private ConfigService configService;

    private static Map<String, String> manufacturerMap = new ConcurrentHashMap<>();


    public NacosWebHookConfigOperation(String serverAddr) throws NacosException {
        Properties properties = new Properties();

        properties.put(PropertyKeyConst.SERVER_ADDR, serverAddr);
        configService = ConfigFactory.createConfigService(serverAddr);

        String manufacturers = configService.getConfig("manufacturers" + DATA_ID_EXTENSION, "webhook", TIMEOUT_MS);
        if (manufacturers == null) {
            configService.publishConfig("manufacturers" + DATA_ID_EXTENSION, "webhook", JsonUtils.serialize(new ManufacturerObject()),
                ConfigType.JSON.getType());
        }


    }

    @Override
    public Integer insertWebHookConfig(WebHookConfig webHookConfig) {
        Boolean result = false;
        String manufacturerName = webHookConfig.getManufacturerName();
        try {
            // Check whether the configuration already exists
            if (configService.getConfig(webHookConfig.getManufacturerEventName() + DATA_ID_EXTENSION, GROUP_PREFIX + manufacturerName, TIMEOUT_MS)
                != null) {
                logger.error("insertWebHookConfig failed, config is existed");
                return 0;
            }

            // The vendor name is groupId and the vendor event name is dataId
            result = configService.publishConfig(webHookConfig.getManufacturerEventName() + DATA_ID_EXTENSION, GROUP_PREFIX + manufacturerName,
                JsonUtils.serialize(webHookConfig), ConfigType.JSON.getType());
        } catch (NacosException e) {
            logger.error("insertWebHookConfig failed", e);
        }
        if (result) {
            // Update the collection
            try {
                String manufacturersContent = configService.getConfig("manufacturers" + DATA_ID_EXTENSION, "webhook", TIMEOUT_MS);
                ManufacturerObject manufacturerObject = StringUtil.isNullOrEmpty(manufacturersContent)
                    ? new ManufacturerObject() :
                    JsonUtils.deserialize(manufacturersContent, ManufacturerObject.class);
                manufacturerObject.addManufacturer(manufacturerName);
                manufacturerObject.getManufacturerEvents(manufacturerName).add(webHookConfig.getManufacturerEventName());
                configService.publishConfig("manufacturers" + DATA_ID_EXTENSION, "webhook", JsonUtils.serialize(manufacturerObject),
                    ConfigType.JSON.getType());
            } catch (NacosException e) {
                e.printStackTrace();
            }

        }
        return result ? 1 : 0;
    }

    @Override
    public Integer updateWebHookConfig(WebHookConfig webHookConfig) {
        Boolean result = false;
        String manufacturerName = webHookConfig.getManufacturerName();
        try {
            // Check whether the configuration exists
            if (configService.getConfig(webHookConfig.getManufacturerEventName() + DATA_ID_EXTENSION, GROUP_PREFIX + manufacturerName, TIMEOUT_MS)
                == null) {
                logger.error("updateWebHookConfig failed, config is not existed");
                return 0;
            }
            result = configService.publishConfig(webHookConfig.getManufacturerEventName() + DATA_ID_EXTENSION, GROUP_PREFIX + manufacturerName,
                JsonUtils.serialize(webHookConfig), ConfigType.JSON.getType());
        } catch (NacosException e) {
            logger.error("updateWebHookConfig failed", e);
        }
        return result ? 1 : 0;
    }

    @Override
    public Integer deleteWebHookConfig(WebHookConfig webHookConfig) {
        Boolean result = false;
        String manufacturerName = webHookConfig.getManufacturerName();
        try {
            result = configService.removeConfig(webHookConfig.getManufacturerEventName() + DATA_ID_EXTENSION, GROUP_PREFIX + manufacturerName);
        } catch (NacosException e) {
            logger.error("deleteWebHookConfig failed", e);
        }
        if (result) {
            try {
                String manufacturersContent = configService.getConfig("manufacturers" + DATA_ID_EXTENSION, "webhook", TIMEOUT_MS);
                if (!StringUtil.isNullOrEmpty(manufacturersContent)) {
                    ManufacturerObject manufacturerObject = JsonUtils.deserialize(manufacturersContent, ManufacturerObject.class);
                    manufacturerObject.getManufacturerEvents(manufacturerName).remove(webHookConfig.getManufacturerEventName());
                    configService.publishConfig("manufacturers" + DATA_ID_EXTENSION, "webhook", JsonUtils.serialize(manufacturerObject),
                        ConfigType.JSON.getType());
                }
            } catch (NacosException e) {
                e.printStackTrace();
            }

        }
        return result ? 1 : 0;
    }

    @Override
    public WebHookConfig queryWebHookConfigById(WebHookConfig webHookConfig) {
        try {
            String content = configService
                .getConfig(webHookConfig.getManufacturerEventName() + DATA_ID_EXTENSION, GROUP_PREFIX + webHookConfig.getManufacturerName(),
                    TIMEOUT_MS);
            return JsonUtils.deserialize(content, WebHookConfig.class);
        } catch (NacosException e) {
            logger.error("updateWebHookConfig failed", e);
        }
        return null;
    }

    @Override
    public List<WebHookConfig> queryWebHookConfigByManufacturer(WebHookConfig webHookConfig, Integer pageNum,
                                                                Integer pageSize) {
        List<WebHookConfig> webHookConfigs = new ArrayList<>();
        String manufacturerName = webHookConfig.getManufacturerName();
        int startIndex = (pageNum - 1) * pageSize;
        int endIndex = pageNum * pageSize - 1;
        // Find all event names for vendors
        try {
            String manufacturersContent = configService.getConfig("manufacturers" + DATA_ID_EXTENSION, "webhook", TIMEOUT_MS);
            if (!StringUtil.isNullOrEmpty(manufacturersContent)) {
                ManufacturerObject manufacturerObject = JsonUtils.deserialize(manufacturersContent, ManufacturerObject.class);
                List<String> manufacturerEvents = manufacturerObject.getManufacturerEvents(manufacturerName);
                if (manufacturerEvents.size() > startIndex) {
                    for (int i = startIndex; i < endIndex && i < manufacturerEvents.size(); i++) {
                        // Because the nacos API does not provide a batch configuration fetch interface, the query can only be traversed
                        String content =
                            configService.getConfig(manufacturerEvents.get(i) + DATA_ID_EXTENSION, GROUP_PREFIX + manufacturerName, TIMEOUT_MS);
                        webHookConfigs.add(JsonUtils.deserialize(content, WebHookConfig.class));
                    }
                }
            }
        } catch (NacosException e) {
            logger.error("queryWebHookConfigByManufacturer failed", e);
        }
        return webHookConfigs;
    }

}
