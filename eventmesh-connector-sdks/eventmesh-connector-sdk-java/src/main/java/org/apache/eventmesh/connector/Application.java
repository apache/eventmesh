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
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.eventmesh.connector;

import org.apache.eventmesh.connector.api.config.Config;
import org.apache.eventmesh.connector.api.config.SinkConfig;
import org.apache.eventmesh.connector.api.config.SourceConfig;
import org.apache.eventmesh.connector.api.connector.Connector;
import org.apache.eventmesh.connector.api.sink.Sink;
import org.apache.eventmesh.connector.api.source.Source;
import org.apache.eventmesh.connector.util.ConfigUtil;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Application {

    public static void run(Class<? extends Connector> clazz) throws Exception {
        Connector connector;
        try {
            connector = clazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            log.error("new connector error", e);
            return;
        }
        Config config;
        try {
            config = ConfigUtil.parse(connector.configClass());
            // offset storage, memory default
//            KVStoreFactory.setStoreConfig(config.getStoreConfig());
        } catch (Exception e) {
            log.error("parse config error", e);
            return;
        }
        try {
            connector.init(config);
        } catch (Exception e) {
            log.error("connector {} initialize error", connector.name(), e);
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
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            worker.stop();
            log.info("connector {} stopped", connector.name());
        }));
        worker.start();
        log.info("connector {} started", connector.name());
    }

    private static boolean isAssignableFrom(Class<?> c, Class<?> cls) {
        Class<?>[] clazzArr = c.getInterfaces();
        for (Class<?> clazz : clazzArr) {
            if (clazz.isAssignableFrom(cls)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSink(Class<?> c) {
        while (c != null && c != Object.class) {
            if (isAssignableFrom(c, Sink.class)) {
                return true;
            }
            c = c.getSuperclass();
        }
        return false;
    }

    private static boolean isSource(Class<?> c) {
        while (c != null && c != Object.class) {
            if (isAssignableFrom(c, Source.class)) {
                return true;
            }
            c = c.getSuperclass();
        }
        return false;
    }
}
