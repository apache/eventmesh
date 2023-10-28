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

package org.apache.eventmesh.webhook.admin;

import static org.apache.eventmesh.webhook.api.WebHookOperationConstant.DATA_ID_EXTENSION;
import static org.apache.eventmesh.webhook.api.WebHookOperationConstant.GROUP_PREFIX;
import static org.apache.eventmesh.webhook.api.WebHookOperationConstant.MANUFACTURERS_DATA_ID;
import static org.apache.eventmesh.webhook.api.WebHookOperationConstant.TIMEOUT_MS;

import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.webhook.api.Manufacturer;
import org.apache.eventmesh.webhook.api.WebHookConfig;
import org.apache.eventmesh.webhook.api.WebHookConfigOperation;
import org.apache.eventmesh.webhook.api.WebHookOperationConstant;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import com.alibaba.nacos.api.config.ConfigFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.ConfigType;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.shaded.io.grpc.netty.shaded.io.netty.util.internal.StringUtil;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NacosWebHookConfigOperation implements WebHookConfigOperation {

    private static final String CONSTANTS_WEBHOOK = "webhook";

    private final ConfigService configService;

    public NacosWebHookConfigOperation(final Properties properties) throws NacosException {
        configService = ConfigFactory.createConfigService(properties);

        final String manufacturers = configService.getConfig(MANUFACTURERS_DATA_ID, CONSTANTS_WEBHOOK, TIMEOUT_MS);
        if (manufacturers == null) {
            configService.publishConfig(MANUFACTURERS_DATA_ID, CONSTANTS_WEBHOOK,
                JsonUtils.toJSONString(new Manufacturer()), ConfigType.JSON.getType());
        }

    }

    @Override
    public Integer insertWebHookConfig(final WebHookConfig webHookConfig) {
        if (!webHookConfig.getCallbackPath().startsWith(WebHookOperationConstant.CALLBACK_PATH_PREFIX)) {
            if (log.isErrorEnabled()) {
                log.error("webhookConfig callback path must start with {}",
                    WebHookOperationConstant.CALLBACK_PATH_PREFIX);
            }
            return 0;
        }

        Boolean result;
        final String manufacturerName = webHookConfig.getManufacturerName();
        try {
            if (configService.getConfig(getWebHookConfigDataId(webHookConfig),
                getManuGroupId(webHookConfig), TIMEOUT_MS) != null) {
                if (log.isErrorEnabled()) {
                    log.error("insertWebHookConfig failed, config has existed");
                }
                return 0;
            }
            result = configService.publishConfig(getWebHookConfigDataId(webHookConfig), getManuGroupId(webHookConfig),
                JsonUtils.toJSONString(webHookConfig), ConfigType.JSON.getType());
        } catch (NacosException e) {
            log.error("insertWebHookConfig failed", e);
            return 0;
        }

        if (result) {
            // update manufacturer config
            try {
                final Manufacturer manufacturer = getManufacturersInfo();
                manufacturer.addManufacturer(manufacturerName);
                manufacturer.getManufacturerEvents(manufacturerName).add(getWebHookConfigDataId(webHookConfig));
                configService.publishConfig(MANUFACTURERS_DATA_ID, CONSTANTS_WEBHOOK,
                    JsonUtils.toJSONString(manufacturer), ConfigType.JSON.getType());
            } catch (NacosException e) {
                log.error("update manufacturersInfo error", e);
                // rollback insert
                try {
                    configService.removeConfig(getWebHookConfigDataId(webHookConfig), getManuGroupId(webHookConfig));
                } catch (NacosException ex) {
                    log.error("rollback insertWebHookConfig failed", e);
                }
            }
        }
        return result ? 1 : 0;
    }

    @Override
    public Integer updateWebHookConfig(final WebHookConfig webHookConfig) {
        boolean result = false;
        try {
            if (configService.getConfig(getWebHookConfigDataId(webHookConfig), getManuGroupId(webHookConfig),
                TIMEOUT_MS) == null) {
                if (log.isErrorEnabled()) {
                    log.error("updateWebHookConfig failed, config is not existed");
                }
                return 0;
            }
            result = configService.publishConfig(getWebHookConfigDataId(webHookConfig),
                getManuGroupId(webHookConfig), JsonUtils.toJSONString(webHookConfig), ConfigType.JSON.getType());
        } catch (NacosException e) {
            log.error("updateWebHookConfig failed", e);
        }
        return result ? 1 : 0;
    }

    @Override
    public Integer deleteWebHookConfig(final WebHookConfig webHookConfig) {
        boolean result = false;
        final String manufacturerName = webHookConfig.getManufacturerName();

        try {
            result = configService.removeConfig(getWebHookConfigDataId(webHookConfig), getManuGroupId(webHookConfig));
        } catch (NacosException e) {
            log.error("deleteWebHookConfig failed", e);
        }
        if (result) {
            try {
                final Manufacturer manufacturer = getManufacturersInfo();
                manufacturer.getManufacturerEvents(manufacturerName).remove(getWebHookConfigDataId(webHookConfig));
                configService.publishConfig(MANUFACTURERS_DATA_ID, CONSTANTS_WEBHOOK,
                    JsonUtils.toJSONString(manufacturer), ConfigType.JSON.getType());
            } catch (NacosException e) {
                log.error("update manufacturersInfo error", e);
            }
        }
        return result ? 1 : 0;
    }

    /**
     * Query WebHook configuration information based on the WebHook callback path specified in {@link WebHookConfig}.
     */
    @Override
    public WebHookConfig queryWebHookConfigById(final WebHookConfig webHookConfig) {
        try {
            final String content = configService.getConfig(getWebHookConfigDataId(webHookConfig),
                getManuGroupId(webHookConfig), TIMEOUT_MS);
            return JsonUtils.parseObject(content, WebHookConfig.class);
        } catch (NacosException e) {
            log.error("queryWebHookConfigById failed", e);
        }
        return null;
    }

    @Override
    public List<WebHookConfig> queryWebHookConfigByManufacturer(final WebHookConfig webHookConfig,
        final Integer pageNum,
        final Integer pageSize) {
        final List<WebHookConfig> webHookConfigs = new ArrayList<>();
        final String manufacturerName = webHookConfig.getManufacturerName();

        // get manufacturer event list
        try {
            final List<String> manufacturerEvents = getManufacturersInfo().getManufacturerEvents(manufacturerName);
            final int startIndex = (pageNum - 1) * pageSize;
            final int endIndex = pageNum * pageSize - 1;
            if (manufacturerEvents.size() > startIndex) {
                // nacos API is not able to get all config, so use foreach
                for (int i = startIndex; i < endIndex && i < manufacturerEvents.size(); i++) {
                    final String content = configService.getConfig(manufacturerEvents.get(i) + DATA_ID_EXTENSION,
                        getManuGroupId(webHookConfig), TIMEOUT_MS);
                    webHookConfigs.add(JsonUtils.parseObject(content, WebHookConfig.class));
                }
            }
        } catch (NacosException e) {
            log.error("queryWebHookConfigByManufacturer failed", e);
        }
        return webHookConfigs;
    }

    /**
     * Escape callback path to a valid dataId.
     */
    private String getWebHookConfigDataId(final WebHookConfig webHookConfig) {
        String dataId = webHookConfig.getCallbackPath();
        if (dataId.startsWith("/")) {
            // remove the first slash
            dataId = dataId.substring(1);
        }
        // then replace the subsequent invalid chars with dots
        return dataId.replaceAll("[@#$%^&*,/\\\\]", ".") + DATA_ID_EXTENSION;
    }

    private String getManuGroupId(final WebHookConfig webHookConfig) {
        return GROUP_PREFIX + webHookConfig.getManufacturerName();
    }

    private Manufacturer getManufacturersInfo() throws NacosException {
        final String manufacturersContent = configService.getConfig(MANUFACTURERS_DATA_ID, CONSTANTS_WEBHOOK, TIMEOUT_MS);
        return StringUtil.isNullOrEmpty(manufacturersContent)
            ? new Manufacturer()
            : JsonUtils.parseObject(manufacturersContent, Manufacturer.class);
    }

}
