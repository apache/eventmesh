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

import org.apache.eventmesh.common.config.ConfigService;
import org.apache.eventmesh.common.config.ConfigurationWrapper;
import org.apache.eventmesh.webhook.api.WebHookConfigOperation;
import org.apache.eventmesh.webhook.api.WebHookOperationConstant;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class AdminWebHookConfigOperationManage {

    private static final Map<String, Class<? extends WebHookConfigOperation>> map = new HashMap<>();

    static {
        map.put("file", FileWebHookConfigOperation.class);
        map.put("nacos", NacosWebHookConfigOperation.class);
    }

    public Logger logger = LoggerFactory.getLogger(this.getClass());

    private ConfigurationWrapper configurationWrapper;

    private WebHookConfigOperation webHookConfigOperation;

    public void setConfigurationWrapper(ConfigurationWrapper configurationWrapper) {
        this.configurationWrapper = configurationWrapper;
    }

    public WebHookConfigOperation getWebHookConfigOperation() {
        return webHookConfigOperation;
    }

    public void init() throws Exception {

        if (!configurationWrapper.getBoolProp(WebHookOperationConstant.ADMIN_START_CONFIG_NAME, false)) {
            return;
        }

        String operationMode = configurationWrapper.getProp(WebHookOperationConstant.OPERATION_MODE_CONFIG_NAME);

        if (!map.containsKey(operationMode)) {
            throw new IllegalStateException("operationMode is not supported.");
        }

        Constructor<? extends WebHookConfigOperation> constructor = map.get(operationMode).getDeclaredConstructor(Properties.class);
        constructor.setAccessible(true);
        try {
            Properties properties = configurationWrapper.getPropertiesByConfig("eventMesh.webHook." + operationMode + "Mode", true);
            logger.info("operationMode is {}  properties is {} ", operationMode, properties);
            this.webHookConfigOperation = constructor.newInstance(properties);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            logger.error("can't find WebHookConfigOperation implementation");
            throw new Exception("can't find WebHookConfigOperation implementation", e);
        }
    }
}
