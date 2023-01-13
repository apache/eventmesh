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

import static org.apache.eventmesh.webhook.api.WebHookOperationConstant.OPERATION_MODE_FILE;
import static org.apache.eventmesh.webhook.api.WebHookOperationConstant.OPERATION_MODE_NACOS;

import org.apache.eventmesh.common.config.ConfigService;
import org.apache.eventmesh.webhook.api.WebHookConfigOperation;
import org.apache.eventmesh.webhook.config.AdminConfiguration;

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
        map.put(OPERATION_MODE_FILE, FileWebHookConfigOperation.class);
        map.put(OPERATION_MODE_NACOS, NacosWebHookConfigOperation.class);
    }

    public Logger logger = LoggerFactory.getLogger(this.getClass());

    private AdminConfiguration adminConfiguration;

    private WebHookConfigOperation webHookConfigOperation;

    public WebHookConfigOperation getWebHookConfigOperation() {
        return webHookConfigOperation;
    }

    public void init() throws Exception {
        adminConfiguration = ConfigService.getInstance().buildConfigInstance(AdminConfiguration.class);
        if (!adminConfiguration.isAdminStart()) {
            return;
        }

        String operationMode = adminConfiguration.getOperationMode();
        if (!map.containsKey(operationMode)) {
            throw new IllegalStateException("operationMode is not supported.");
        }

        Constructor<? extends WebHookConfigOperation> constructor =
                map.get(operationMode).getDeclaredConstructor(Properties.class);
        constructor.setAccessible(true);
        Properties operationProperties = adminConfiguration.getOperationProperties();
        try {
            logger.info("operationMode is {}  properties is {} ", operationMode, operationProperties);
            this.webHookConfigOperation = constructor.newInstance(operationProperties);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            logger.error("can't find WebHookConfigOperation implementation");
            throw new Exception("can't find WebHookConfigOperation implementation", e);
        }
    }
}
