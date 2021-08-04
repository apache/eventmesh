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

package org.apache.eventmesh.runtime.configuration;

import com.google.common.util.concurrent.RateLimiter;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.EventMeshRuntimeException;
import org.apache.eventmesh.common.config.YamlConfigurationReader;

import java.io.File;
import java.io.IOException;

public class EventMeshHTTPConfiguration {

    private static YamlConfigurationReader yamlConfigurationReader;

    public static int httpServerPort = 10105;

    public static RateLimiter eventMeshServerBatchMsgNumLimiter = RateLimiter.create(20000);

    public static boolean eventMeshServerBatchMsgBatchEnabled = Boolean.TRUE;

    public static int eventMeshServerBatchMsgThreadNum = 10;

    public static int eventMeshServerSendMsgThreadNum = 8;

    public static int eventMeshServerPushMsgThreadNum = 8;

    public static int eventMeshServerReplyMsgThreadNum = 8;

    public static int eventMeshServerClientManageThreadNum = 4;

    public static int eventMeshServerRegistryThreadNum = 10;

    public static int eventMeshServerAdminThreadNum = 2;

    public static int eventMeshServerRetryThreadNum = 2;

    public static int eventMeshServerPullRegistryInterval = 30000;

    public static int eventMeshServerAsyncAccumulationThreshold = 1000;

    public static int eventMeshServerRetryBlockQSize = 10000;

    public static int eventMeshServerBatchBlockQSize = 1000;

    public static int eventMeshServerSendMsgBlockQSize = 1000;

    public static int eventMeshServerPushMsgBlockQSize = 1000;

    public static int eventMeshServerClientManageBlockQSize = 1000;

    public static int eventMeshServerBusyCheckInterval = 1000;

    public static boolean eventMeshServerConsumerEnabled = false;

    public static boolean eventMeshServerUseTls = false;

    static {
        String confPath = System.getProperty("confPath", System.getenv("confPath"));
        String yamlConfigFilePath = confPath + File.separator + Constants.EVENTMESH_COMMON_PROPERTY;
        try {
            yamlConfigurationReader = new YamlConfigurationReader(yamlConfigFilePath);
        } catch (IOException e) {
            throw new EventMeshRuntimeException(String.format("config file: %s is not exist", yamlConfigFilePath), e);
        }
        refreshConfig();
    }

    private static void refreshConfig() {
        httpServerPort = yamlConfigurationReader.getInt(ConfKeys.KEYS_EVENTMESH_SERVER_HTTP_PORT, httpServerPort);

        eventMeshServerBatchMsgNumLimiter = RateLimiter.create(yamlConfigurationReader.getInt(ConfKeys.KEYS_EVENTMESH_BATCHMSG_RATELIMITER, 20000));
        eventMeshServerBatchMsgBatchEnabled = yamlConfigurationReader.getBool(ConfKeys.KEYS_EVENTMESH_BATCHMSG_BATCH_ENABLED, eventMeshServerBatchMsgBatchEnabled);
        eventMeshServerBatchMsgThreadNum = yamlConfigurationReader.getInt(ConfKeys.KEYS_EVENTMESH_BATCHMSG_THREAD_NUM, eventMeshServerBatchMsgThreadNum);

        eventMeshServerSendMsgThreadNum = yamlConfigurationReader.getInt(ConfKeys.KEYS_EVENTMESH_SENDMSG_THREAD_NUM, eventMeshServerSendMsgThreadNum);
        eventMeshServerPushMsgThreadNum = yamlConfigurationReader.getInt(ConfKeys.KEYS_EVENTMESH_PUSHMSG_THREAD_NUM, eventMeshServerPushMsgThreadNum);

        eventMeshServerReplyMsgThreadNum = yamlConfigurationReader.getInt(ConfKeys.KEYS_EVENTMESH_REPLYMSG_THREAD_NUM, eventMeshServerReplyMsgThreadNum);

        eventMeshServerClientManageThreadNum = yamlConfigurationReader.getInt(ConfKeys.KEYS_EVENTMESH_CLIENTMANAGE_THREAD_NUM, eventMeshServerClientManageThreadNum);
        eventMeshServerRegistryThreadNum = yamlConfigurationReader.getInt(ConfKeys.KEYS_EVENTMESH_REGISTRY_THREAD_NUM, eventMeshServerRegistryThreadNum);

        eventMeshServerAdminThreadNum = yamlConfigurationReader.getInt(ConfKeys.KEYS_EVENTMESH_ADMIN_THREAD_NUM, eventMeshServerAdminThreadNum);
        eventMeshServerRetryThreadNum = yamlConfigurationReader.getInt(ConfKeys.KEY_EVENTMESH_RETRY_THREAD_NUM, eventMeshServerRetryThreadNum);
        eventMeshServerPullRegistryInterval = yamlConfigurationReader.getInt(ConfKeys.KEYS_EVENTMESH_PULL_REGISTRY_INTERVAL, eventMeshServerPullRegistryInterval);
        eventMeshServerAsyncAccumulationThreshold = yamlConfigurationReader.getInt(ConfKeys.KEYS_EVENTMESH_ASYNC_ACCUMULATION_THRESHOLD, eventMeshServerAsyncAccumulationThreshold);

        eventMeshServerRetryBlockQSize = yamlConfigurationReader.getInt(ConfKeys.KEY_EVENTMESH_RETRY_BLOCKQ_SIZE, eventMeshServerRetryBlockQSize);
        eventMeshServerBatchBlockQSize = yamlConfigurationReader.getInt(ConfKeys.KEY_EVENTMESH_BATCHMSG_BLOCKQ_SIZE, eventMeshServerBatchBlockQSize);
        eventMeshServerSendMsgBlockQSize = yamlConfigurationReader.getInt(ConfKeys.KEY_EVENTMESH_SENDMSG_BLOCKQ_SIZE, eventMeshServerSendMsgBlockQSize);
        eventMeshServerPushMsgBlockQSize = yamlConfigurationReader.getInt(ConfKeys.KEY_EVENTMESH_PUSHMSG_BLOCKQ_SIZE, eventMeshServerPushMsgBlockQSize);
        eventMeshServerClientManageBlockQSize = yamlConfigurationReader.getInt(ConfKeys.KEY_EVENTMESH_CLIENTM_BLOCKQ_SIZE, eventMeshServerClientManageBlockQSize);
        eventMeshServerBusyCheckInterval = yamlConfigurationReader.getInt(ConfKeys.KEY_EVENTMESH_BUSY_CHECK_INTERVAL, eventMeshServerBusyCheckInterval);
        eventMeshServerConsumerEnabled = yamlConfigurationReader.getBool(ConfKeys.KEY_EVENTMESH_CONSUMER_ENABLED, eventMeshServerConsumerEnabled);
        eventMeshServerUseTls = yamlConfigurationReader.getBool(ConfKeys.KEY_EVENTMESH_HTTPS_ENABLED, eventMeshServerUseTls);

    }

    static class ConfKeys {

        public static String KEYS_EVENTMESH_SERVER_HTTP_PORT = "eventMesh.server.http.port";

        public static String KEYS_EVENTMESH_BATCHMSG_THREAD_NUM = "eventMesh.server.batchmsg.threads.num";

        public static String KEYS_EVENTMESH_BATCHMSG_RATELIMITER = "eventMesh.server.batchmsg.speed.ratelimiter";

        public static String KEYS_EVENTMESH_BATCHMSG_BATCH_ENABLED = "eventMesh.server.batchmsg.batch.enabled";

        public static String KEYS_EVENTMESH_ASYNC_ACCUMULATION_THRESHOLD = "eventMesh.server.async.accumulation.threshold";

        public static String KEY_EVENTMESH_BUSY_CHECK_INTERVAL = "eventMesh.server.busy.check.interval";

        public static String KEYS_EVENTMESH_SENDMSG_THREAD_NUM = "eventMesh.server.sendmsg.threads.num";

        public static String KEYS_EVENTMESH_REPLYMSG_THREAD_NUM = "eventMesh.server.replymsg.threads.num";

        public static String KEYS_EVENTMESH_PUSHMSG_THREAD_NUM = "eventMesh.server.pushmsg.threads.num";

        public static String KEYS_EVENTMESH_REGISTRY_THREAD_NUM = "eventMesh.server.registry.threads.num";

        public static String KEYS_EVENTMESH_CLIENTMANAGE_THREAD_NUM = "eventMesh.server.clientmanage.threads.num";

        public static String KEYS_EVENTMESH_ADMIN_THREAD_NUM = "eventMesh.server.admin.threads.num";

        public static String KEY_EVENTMESH_RETRY_THREAD_NUM = "eventMesh.server.retry.threads.num";

        public static String KEYS_EVENTMESH_PULL_REGISTRY_INTERVAL = "eventMesh.server.pull.registry.interval";

        public static String KEY_EVENTMESH_RETRY_BLOCKQ_SIZE = "eventMesh.server.retry.blockQ.size";

        public static String KEY_EVENTMESH_BATCHMSG_BLOCKQ_SIZE = "eventMesh.server.batchmsg.blockQ.size";

        public static String KEY_EVENTMESH_SENDMSG_BLOCKQ_SIZE = "eventMesh.server.sendmsg.blockQ.size";

        public static String KEY_EVENTMESH_PUSHMSG_BLOCKQ_SIZE = "eventMesh.server.pushmsg.blockQ.size";

        public static String KEY_EVENTMESH_CLIENTM_BLOCKQ_SIZE = "eventMesh.server.clientM.blockQ.size";

        public static String KEY_EVENTMESH_CONSUMER_ENABLED = "eventMesh.server.consumer.enabled";

        public static String KEY_EVENTMESH_HTTPS_ENABLED = "eventMesh.server.useTls.enabled";
    }
}
