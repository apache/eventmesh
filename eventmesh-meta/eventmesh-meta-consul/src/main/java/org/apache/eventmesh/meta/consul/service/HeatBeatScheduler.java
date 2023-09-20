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

package org.apache.eventmesh.meta.consul.service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.ecwid.consul.v1.ConsulClient;
import com.ecwid.consul.v1.agent.model.NewService;

public class HeatBeatScheduler {

    private final ConsulClient consulClient;

    private final ConcurrentHashMap<String, NewService> heartBeatMap = new ConcurrentHashMap<>();

    private final ScheduledExecutorService heartbeatServiceExecutor = new ScheduledThreadPoolExecutor(
        Runtime.getRuntime().availableProcessors(),
        r -> {
            Thread thread = new Thread(r);
            thread.setDaemon(true);
            thread.setName("ConsulHeartbeatService");
            return thread;
        });

    public HeatBeatScheduler(ConsulClient consulClient) {
        this.consulClient = consulClient;
    }

    /**
     * start service heartbeat
     *
     * @param newService service
     * @param aclToken   token
     */
    protected void startHeartBeat(NewService newService, String aclToken) {
        heartbeatServiceExecutor.execute(new HeartBeat(newService, aclToken));
        heartBeatMap.put(newService.getName(), newService);
    }

    /**
     * stop service heartbeat
     *
     * @param newService service
     */
    private void stopHeartBeat(NewService newService) {
        heartBeatMap.remove(newService.getName());
    }

    class HeartBeat implements Runnable {

        private static final String CHECK_ID_PREFIX = "service:";

        private String checkId;

        private final String aclToken;

        private final NewService instance;

        public HeartBeat(NewService instance, String aclToken) {
            this.instance = instance;
            this.checkId = instance.getId();
            this.aclToken = aclToken;
            if (!checkId.startsWith(CHECK_ID_PREFIX)) {
                checkId = CHECK_ID_PREFIX + checkId;
            }
        }

        @Override
        public void run() {
            try {
                if (aclToken != null) {
                    consulClient.agentCheckPass(checkId, aclToken);
                    return;
                }
                if (heartBeatMap.containsValue(instance)) {
                    consulClient.agentCheckPass(checkId);
                }
            } finally {
                heartbeatServiceExecutor.schedule(this, 3000, TimeUnit.SECONDS);
            }
        }

    }
}
