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

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AdminWebHookConfigOperationManager {

    private static final Map<String, Class<? extends WebHookConfigOperation>> WEBHOOK_CONFIG_OPERATION_MAP = new HashMap<>();

    static {
        WEBHOOK_CONFIG_OPERATION_MAP.put(OPERATION_MODE_FILE, FileWebHookConfigOperation.class);
        WEBHOOK_CONFIG_OPERATION_MAP.put(OPERATION_MODE_NACOS, NacosWebHookConfigOperation.class);
    }

    private transient AdminConfiguration adminConfiguration;

    private transient WebHookConfigOperation webHookConfigOperation;

    public WebHookConfigOperation getWebHookConfigOperation() {
        return webHookConfigOperation;
    }

    public void init() throws InvocationTargetException, InstantiationException, IllegalAccessException, NoSuchMethodException {

        adminConfiguration = ConfigService.getInstance().buildConfigInstance(AdminConfiguration.class);
        if (!adminConfiguration.isAdminStart()) {
            return;
        }

        final String operationMode = adminConfiguration.getOperationMode();
        if (!WEBHOOK_CONFIG_OPERATION_MAP.containsKey(operationMode)) {
            throw new IllegalStateException("operationMode is not supported.");
        }

        // Affects which implementation of the WebHookConfigOperation interface is used.
        final Constructor<? extends WebHookConfigOperation> constructor =
            WEBHOOK_CONFIG_OPERATION_MAP.get(operationMode).getDeclaredConstructor(Properties.class);
        // Save the original accessibility of constructor
        final boolean oldAccessible = constructor.isAccessible();
        try {
            constructor.setAccessible(true);
            final Properties operationProperties = adminConfiguration.getOperationProperties();
            if (log.isInfoEnabled()) {
                log.info("operationMode is {}  properties is {} ", operationMode, operationProperties);
            }
            this.webHookConfigOperation = constructor.newInstance(operationProperties);
        } finally {
            // Restore the original accessibility of constructor
            constructor.setAccessible(oldAccessible);
        }

    }
}
