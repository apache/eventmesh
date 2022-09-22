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

import static org.apache.eventmesh.webhook.api.WebHookOperationConstant.DATA_ID_EXTENSION;
import static org.apache.eventmesh.webhook.api.WebHookOperationConstant.GROUP_PREFIX;
import static org.apache.eventmesh.webhook.api.WebHookOperationConstant.MANUFACTURERS_DATA_ID;
import static org.apache.eventmesh.webhook.api.WebHookOperationConstant.TIMEOUT_MS;

import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.webhook.api.ManufacturerObject;
import org.apache.eventmesh.webhook.api.WebHookConfig;
import org.apache.eventmesh.webhook.api.WebHookConfigOperation;
import org.apache.eventmesh.webhook.api.WebHookOperationConstant;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.nacos.api.config.ConfigFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.ConfigType;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.shaded.io.grpc.netty.shaded.io.netty.util.internal.StringUtil;


public class NacosWebHookConfigOperation implements WebHookConfigOperation {

    private static final Logger logger = LoggerFactory.getLogger(NacosWebHookConfigOperation.class);
    private static final String CONSTANTS_WEBHOOK = "webhook";

    private final ConfigService configService;


    public NacosWebHookConfigOperation(Properties properties) throws NacosException {
        configService = ConfigFactory.createConfigService(properties);

        String manufacturers = configService.getConfig(MANUFACTURERS_DATA_ID, CONSTANTS_WEBHOOK, TIMEOUT_MS);
        if (manufacturers == null) {
            configService.publishConfig(MANUFACTURERS_DATA_ID, CONSTANTS_WEBHOOK,
                JsonUtils.serialize(new ManufacturerObject()), ConfigType.JSON.getType());
        }

    }

    @Override
    public Integer insertWebHookConfig(WebHookConfig webHookConfig) {
        if (!webHookConfig.getCallbackPath().startsWith(WebHookOperationConstant.CALLBACK_PATH_PREFIX)) {
            logger.error("webhookConfig callback path must start with {}", WebHookOperationConstant.CALLBACK_PATH_PREFIX);
            return 0;
        }
        Boolean result;
        String manufacturerName = webHookConfig.getManufacturerName();
        try {
            if (configService.getConfig(getWebHookConfigDataId(webHookConfig), getManuGroupId(webHookConfig), TIMEOUT_MS) != null) {
                logger.error("insertWebHookConfig failed, config has existed");
                return 0;
            }
            result = configService.publishConfig(getWebHookConfigDataId(webHookConfig), getManuGroupId(webHookConfig),
                JsonUtils.serialize(webHookConfig), ConfigType.JSON.getType());
        } catch (NacosException e) {
            logger.error("insertWebHookConfig failed", e);
            return 0;
        }
        if (result) {
            // update manufacturer config
            try {
                ManufacturerObject manufacturerObject = getManufacturersInfo();
                manufacturerObject.addManufacturer(manufacturerName);
                manufacturerObject.getManufacturerEvents(manufacturerName).add(getWebHookConfigDataId(webHookConfig));
                configService.publishConfig(MANUFACTURERS_DATA_ID, CONSTANTS_WEBHOOK,
                    JsonUtils.serialize(manufacturerObject), ConfigType.JSON.getType());
            } catch (NacosException e) {
                logger.error("update manufacturersInfo error", e);
                //rollback insert
                try {
                    configService.removeConfig(getWebHookConfigDataId(webHookConfig), getManuGroupId(webHookConfig));
                } catch (NacosException ex) {
                    logger.error("rollback insertWebHookConfig failed", e);
                }
            }
        }
        return result ? 1 : 0;
    }

    @Override
    public Integer updateWebHookConfig(WebHookConfig webHookConfig) {
        Boolean result = false;
        try {
            if (configService.getConfig(getWebHookConfigDataId(webHookConfig), getManuGroupId(webHookConfig), TIMEOUT_MS) == null) {
                logger.error("updateWebHookConfig failed, config is not existed");
                return 0;
            }
            result = configService.publishConfig(getWebHookConfigDataId(webHookConfig),
                getManuGroupId(webHookConfig), JsonUtils.serialize(webHookConfig), ConfigType.JSON.getType());
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
            result = configService.removeConfig(getWebHookConfigDataId(webHookConfig), getManuGroupId(webHookConfig));
        } catch (NacosException e) {
            logger.error("deleteWebHookConfig failed", e);
        }
        if (result) {
            try {
                ManufacturerObject manufacturerObject = getManufacturersInfo();
                manufacturerObject.getManufacturerEvents(manufacturerName).remove(getWebHookConfigDataId(webHookConfig));
                configService.publishConfig(MANUFACTURERS_DATA_ID, CONSTANTS_WEBHOOK,
                    JsonUtils.serialize(manufacturerObject), ConfigType.JSON.getType());
            } catch (NacosException e) {
                logger.error("update manufacturersInfo error", e);
            }
        }
        return result ? 1 : 0;
    }

    @Override
    public WebHookConfig queryWebHookConfigById(WebHookConfig webHookConfig) {
        try {
            String content = configService.getConfig(getWebHookConfigDataId(webHookConfig), getManuGroupId(webHookConfig), TIMEOUT_MS);
            return JsonUtils.deserialize(content, WebHookConfig.class);
        } catch (NacosException e) {
            logger.error("queryWebHookConfigById failed", e);
        }
        return null;
    }

    @Override
    public List<WebHookConfig> queryWebHookConfigByManufacturer(WebHookConfig webHookConfig, Integer pageNum,
                                                                Integer pageSize) {
        List<WebHookConfig> webHookConfigs = new ArrayList<>();
        String manufacturerName = webHookConfig.getManufacturerName();
        // get manufacturer event list
        try {
            ManufacturerObject manufacturerObject = getManufacturersInfo();
            List<String> manufacturerEvents = manufacturerObject.getManufacturerEvents(manufacturerName);
            int startIndex = (pageNum - 1) * pageSize;
            int endIndex = pageNum * pageSize - 1;
            if (manufacturerEvents.size() > startIndex) {
                // nacos API is not able to get all config, so use foreach
                for (int i = startIndex; i < endIndex && i < manufacturerEvents.size(); i++) {
                    String content = configService.getConfig(manufacturerEvents.get(i) + DATA_ID_EXTENSION,
                        getManuGroupId(webHookConfig), TIMEOUT_MS);
                    webHookConfigs.add(JsonUtils.deserialize(content, WebHookConfig.class));
                }
            }
        } catch (NacosException e) {
            logger.error("queryWebHookConfigByManufacturer failed", e);
        }
        return webHookConfigs;
    }

    /**
     * @param webHookConfig
     * @return
     */
    private String getWebHookConfigDataId(WebHookConfig webHookConfig) {
        try {
            // use URLEncoder.encode before, because the path may contain some speacial char like '/', which is illegal as a data id.
            return URLEncoder.encode(webHookConfig.getCallbackPath(), "UTF-8") + DATA_ID_EXTENSION;
        } catch (UnsupportedEncodingException e) {
            logger.error("get webhookConfig dataId {} failed", webHookConfig.getCallbackPath(), e);
        }
        return webHookConfig.getCallbackPath() + DATA_ID_EXTENSION;
    }

    private String getManuGroupId(WebHookConfig webHookConfig) {
        return GROUP_PREFIX + webHookConfig.getManufacturerName();
    }

    private ManufacturerObject getManufacturersInfo() throws NacosException {
        String manufacturersContent = configService.getConfig(MANUFACTURERS_DATA_ID, CONSTANTS_WEBHOOK, TIMEOUT_MS);
        return StringUtil.isNullOrEmpty(manufacturersContent)
            ? new ManufacturerObject() : JsonUtils.deserialize(manufacturersContent, ManufacturerObject.class);
    }

}
