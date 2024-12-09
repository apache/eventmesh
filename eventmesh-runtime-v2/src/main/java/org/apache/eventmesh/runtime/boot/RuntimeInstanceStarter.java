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
import org.apache.eventmesh.runtime.RuntimeInstanceConfig;
import org.apache.eventmesh.runtime.util.BannerUtil;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RuntimeInstanceStarter {

    public static void main(String[] args) {
        try {
            RuntimeInstanceConfig runtimeInstanceConfig = ConfigService.getInstance().buildConfigInstance(RuntimeInstanceConfig.class);
            RuntimeInstance runtimeInstance = new RuntimeInstance(runtimeInstanceConfig);
            BannerUtil.generateBanner();
            runtimeInstance.init();
            runtimeInstance.start();

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    log.info("runtime shutting down hook begin.");
                    long start = System.currentTimeMillis();
                    runtimeInstance.shutdown();
                    long end = System.currentTimeMillis();
                    log.info("runtime shutdown cost {}ms", end - start);
                } catch (Exception e) {
                    log.error("exception when shutdown {}", e.getMessage(), e);
                }
            }));
        } catch (Throwable e) {
            log.error("runtime start fail {}.", e.getMessage(), e);
            System.exit(-1);
        }

    }
}
