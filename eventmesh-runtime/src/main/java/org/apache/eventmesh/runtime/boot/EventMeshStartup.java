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

package org.apache.eventmesh.runtime.boot;

import org.apache.eventmesh.common.config.ConfigService;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.util.BannerUtil;

import java.io.File;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EventMeshStartup {

    public static void main(String[] args) throws Exception {
        try {
            ConfigService.getInstance()
                .setConfigPath(EventMeshConstants.EVENTMESH_CONF_HOME + File.separator)
                .setRootConfig(EventMeshConstants.EVENTMESH_CONF_FILE);

            EventMeshServer server = new EventMeshServer();
            BannerUtil.generateBanner();
            server.init();
            server.start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    if (log.isInfoEnabled()) {
                        log.info("eventMesh shutting down hook begin.");
                    }
                    long start = System.currentTimeMillis();
                    server.shutdown();
                    long end = System.currentTimeMillis();
                    if (log.isInfoEnabled()) {
                        log.info("eventMesh shutdown cost {}ms", end - start);
                    }
                } catch (Exception e) {
                    log.error("exception when shutdown.", e);
                }
            }));
        } catch (Throwable e) {
            log.error("EventMesh start fail.", e);
            System.exit(-1);
        }

    }
}

