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

package com.webank.eventmesh.runtime.boot;

import com.webank.eventmesh.common.config.ConfigurationWraper;
import com.webank.eventmesh.runtime.configuration.EventMeshTCPConfiguration;
import com.webank.eventmesh.runtime.configuration.EventMeshHTTPConfiguration;
import com.webank.eventmesh.runtime.constants.EventMeshConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public class EventMeshStartup {

    public static Logger logger = LoggerFactory.getLogger(EventMeshStartup.class);

    public static void main(String[] args) throws Exception {
        try{
            ConfigurationWraper configurationWraper =
                    new ConfigurationWraper(EventMeshConstants.EVENTMESH_CONF_HOME
                            + File.separator
                            + EventMeshConstants.EVENTMESH_CONF_FILE, false);
            EventMeshHTTPConfiguration eventMeshHttpConfiguration = new EventMeshHTTPConfiguration(configurationWraper);
            eventMeshHttpConfiguration.init();
            EventMeshTCPConfiguration eventMeshTCPConfiguration = new EventMeshTCPConfiguration(configurationWraper);
            eventMeshTCPConfiguration.init();
            EventMeshServer server = new EventMeshServer(eventMeshHttpConfiguration, eventMeshTCPConfiguration);
            server.init();
            server.start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    logger.info("eventMesh shutting down hook begin...");
                    long start = System.currentTimeMillis();
                    server.shutdown();
                    long end = System.currentTimeMillis();
                    logger.info("eventMesh shutdown cost {}ms", end - start);
                } catch (Exception e) {
                    logger.error("exception when shutdown...", e);
                }
            }));
        }catch (Throwable e){
            logger.error("EventMesh start fail.", e);
            e.printStackTrace();
        }

    }
}

