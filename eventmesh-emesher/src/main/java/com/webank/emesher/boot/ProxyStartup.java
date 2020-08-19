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

package com.webank.emesher.boot;

import com.webank.emesher.configuration.AccessConfiguration;
import com.webank.emesher.configuration.ConfigurationWraper;
import com.webank.emesher.configuration.ProxyConfiguration;
import com.webank.emesher.constants.ProxyConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public class ProxyStartup {

    public static Logger logger = LoggerFactory.getLogger(ProxyStartup.class);

    public static void main(String[] args) throws Exception {
        try{
            ConfigurationWraper configurationWraper =
                    new ConfigurationWraper(ProxyConstants.PROXY_CONF_HOME
                            + File.separator
                            + ProxyConstants.PROXY_CONF_FILE, false);
            ProxyConfiguration proxyConfiguration = new ProxyConfiguration(configurationWraper);
            proxyConfiguration.init();
            AccessConfiguration accessConfiguration = new AccessConfiguration(configurationWraper);
            accessConfiguration.init();
            ProxyServer server = new ProxyServer(proxyConfiguration, accessConfiguration);
            server.init();
            server.start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    logger.info("proxy shutting down hook begin...");
                    long start = System.currentTimeMillis();
                    server.shutdown();
                    long end = System.currentTimeMillis();
                    logger.info("proxy shutdown cost {}ms", end - start);
                } catch (Exception e) {
                    logger.error("exception when shutdown...", e);
                }
            }));
        }catch (Throwable e){
            logger.error("Proxy start fail.", e);
            e.printStackTrace();
        }

    }
}

