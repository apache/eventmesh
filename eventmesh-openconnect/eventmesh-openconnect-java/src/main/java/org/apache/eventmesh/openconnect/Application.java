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

package org.apache.eventmesh.openconnect;

import org.apache.eventmesh.common.config.connector.Config;
import org.apache.eventmesh.common.config.connector.SinkConfig;
import org.apache.eventmesh.common.config.connector.SourceConfig;
import org.apache.eventmesh.openconnect.api.ConnectorCreateService;
import org.apache.eventmesh.openconnect.api.connector.Connector;
import org.apache.eventmesh.openconnect.api.sink.Sink;
import org.apache.eventmesh.openconnect.api.source.Source;
import org.apache.eventmesh.openconnect.util.ConfigUtil;
import org.apache.eventmesh.spi.EventMeshExtensionFactory;

import org.apache.commons.collections4.MapUtils;

import java.util.HashMap;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Application {

    public static final Map<String, Connector> CONNECTOR_MAP = new HashMap<>();

    public static final String CREATE_EXTENSION_KEY = "createExtension";

    private Map<String, String> extensions;

    public Application() {

    }

    public Application(Map<String, String> extensions) {
        this.extensions = extensions;
    }

    public void run(Class<? extends Connector> clazz) throws Exception {

        Connector connector = null;
        try {
            if (MapUtils.isNotEmpty(extensions) && extensions.containsKey(CREATE_EXTENSION_KEY)) {
                String spiKey = extensions.get(CREATE_EXTENSION_KEY);
                ConnectorCreateService<?> createService =
                    EventMeshExtensionFactory.getExtension(ConnectorCreateService.class, spiKey);
                if (createService != null) {
                    connector = createService.create();
                }
            }
            if (connector == null) {
                connector = clazz.getDeclaredConstructor().newInstance();
            }
        } catch (Exception e) {
            log.error("new connector error", e);
            return;
        }
        Config config;
        try {
            config = ConfigUtil.parse(connector.configClass());
        } catch (Exception e) {
            log.error("parse config error", e);
            return;
        }

        ConnectorWorker worker;
        if (isSink(clazz)) {
            worker = new SinkWorker((Sink) connector, (SinkConfig) config);
        } else if (isSource(clazz)) {
            worker = new SourceWorker((Source) connector, (SourceConfig) config);
        } else {
            log.error("class {} is not sink and source", clazz);
            return;
        }
        worker.init();

        CONNECTOR_MAP.putIfAbsent(connector.name(), connector);
        Connector finalConnector = connector;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            worker.stop();
            log.info("connector {} stopped", finalConnector.name());
        }));
        worker.start();
        log.info("connector {} started", connector.name());
    }

    public static boolean isAssignableFrom(Class<?> c, Class<?> cls) {
        Class<?>[] clazzArr = c.getInterfaces();
        for (Class<?> clazz : clazzArr) {
            if (clazz.isAssignableFrom(cls)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isSink(Class<?> c) {
        while (c != null && c != Object.class) {
            if (isAssignableFrom(c, Sink.class)) {
                return true;
            }
            c = c.getSuperclass();
        }
        return false;
    }

    public static boolean isSource(Class<?> c) {
        while (c != null && c != Object.class) {
            if (isAssignableFrom(c, Source.class)) {
                return true;
            }
            c = c.getSuperclass();
        }
        return false;
    }
}
