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

import org.apache.eventmesh.webhook.api.WebHookConfigOperation;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class AdminWebHookConfigOperationManage {

    public Logger logger = LoggerFactory.getLogger(this.getClass());

    private static final Map<String, Class<? extends WebHookConfigOperation>> map = new HashMap<>();

    static {
        map.put("file", FileWebHookConfigOperation.class);
        map.put("nacos", NacosWebHookConfigOperation.class);
    }

    /**
     * Create it in ClientManageController
     *
     * @return WebHookConfigOperation implementation
     */
    public WebHookConfigOperation getHookConfigOperationManage() throws Exception {
        Properties configProperties = readConfigFromConfigFile();
        String operationMode = configProperties.getProperty("eventMesh.webHook.operationMode");

        if (!map.containsKey(operationMode)) {
            throw new IllegalStateException("operationMode is not supported.");
        }

        Constructor<? extends WebHookConfigOperation> constructor = map.get(operationMode).getDeclaredConstructor(String.class);
        constructor.setAccessible(true);
        try {
            return constructor.newInstance(configProperties);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            logger.error("can't find WebHookConfigOperation implementation");
            throw new Exception("can't find WebHookConfigOperation implementation");
        }
    }

    /**
     * Read webHook related configurations from the global configuration file
     */
    public Properties readConfigFromConfigFile() throws IOException {
        Properties configProperties;
        try (final InputStream inputStream =
                 WebHookConfigOperation.class.getClassLoader().getResourceAsStream("eventmesh.properties")) {
            configProperties = new Properties();
            configProperties.load(inputStream);
        }
        return configProperties;
    }

}
