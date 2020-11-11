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

package com.webank.emesher.configuration;

import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.RateLimiter;
import org.apache.commons.lang3.StringUtils;

public class ProxyConfiguration extends CommonConfiguration {

    public int httpServerPort = 10105;

    public RateLimiter proxyServerBatchMsgNumLimiter = RateLimiter.create(20000);

    public boolean proxyServerBatchMsgBatchEnabled = Boolean.TRUE;

    public int proxyServerBatchMsgThreadNum = 10;

    public int proxyServerSendMsgThreadNum = 8;

    public int proxyServerPushMsgThreadNum = 8;

    public int proxyServerReplyMsgThreadNum = 8;

    public int proxyServerClientManageThreadNum = 4;

    public int proxyServerRegistryThreadNum = 10;

    public int proxyServerAdminThreadNum = 2;

    public int proxyServerRetryThreadNum = 2;

    public int proxyServerPullRegistryIntervel = 30000;

    public int proxyServerAsyncAccumulationThreshold = 1000;

    public int proxyServerRetryBlockQSize = 10000;

    public int proxyServerBatchBlockQSize = 1000;

    public int proxyServerSendMsgBlockQSize = 1000;

    public int proxyServerPushMsgBlockQSize = 1000;

    public int proxyServerClientManageBlockQSize = 1000;

    public int proxyServerBusyCheckIntervel = 1000;

    public boolean proxyServerConsumerEnabled = false;

    public boolean proxyServerUseTls = false;

    public ProxyConfiguration(ConfigurationWraper configurationWraper){
        super(configurationWraper);
    }

    public void init(){
        super.init();

        String httpServerPortStr = configurationWraper.getProp(ConfKeys.KEYS_PROXY_SERVER_HTTP_PORT);
        Preconditions.checkState(StringUtils.isNotEmpty(httpServerPortStr) && StringUtils.isNumeric(httpServerPortStr), String.format("%s error", ConfKeys.KEYS_PROXY_SERVER_HTTP_PORT));
        httpServerPort = Integer.valueOf(StringUtils.deleteWhitespace(httpServerPortStr));

        String proxyServerBatchMsgThreadNumStr = configurationWraper.getProp(ConfKeys.KEYS_PROXY_BATCHMSG_THREAD_NUM);
        if (StringUtils.isNotEmpty(proxyServerBatchMsgThreadNumStr) && StringUtils.isNumeric(proxyServerBatchMsgThreadNumStr)) {
            proxyServerBatchMsgThreadNum = Integer.valueOf(StringUtils.deleteWhitespace(proxyServerBatchMsgThreadNumStr));
        }

        String proxyServerBatchMsgNumLimiterStr = configurationWraper.getProp(ConfKeys.KEYS_PROXY_BATCHMSG_RATELIMITER);
        if (StringUtils.isNotEmpty(proxyServerBatchMsgNumLimiterStr) && StringUtils.isNumeric(proxyServerBatchMsgNumLimiterStr)) {
            proxyServerBatchMsgNumLimiter = RateLimiter.create(Double.valueOf(StringUtils.deleteWhitespace(proxyServerBatchMsgNumLimiterStr)));
        }

        String proxyServerBatchMsgBatchEnableStr = configurationWraper.getProp(ConfKeys.KEYS_PROXY_BATCHMSG_BATCH_ENABLED);
        if (StringUtils.isNotBlank(proxyServerBatchMsgBatchEnableStr)) {
            proxyServerBatchMsgBatchEnabled = Boolean.valueOf(proxyServerBatchMsgBatchEnableStr);
        }

        String proxyServerAsyncAccumulationThresholdStr = configurationWraper.getProp(ConfKeys.KEYS_PROXY_ASYNC_ACCUMULATION_THRESHOLD);
        if (StringUtils.isNotEmpty(proxyServerAsyncAccumulationThresholdStr) && StringUtils.isNumeric(proxyServerAsyncAccumulationThresholdStr)) {
            proxyServerAsyncAccumulationThreshold = Integer.valueOf(StringUtils.deleteWhitespace(proxyServerAsyncAccumulationThresholdStr));
        }

        String proxyServerSendMsgThreadNumStr = configurationWraper.getProp(ConfKeys.KEYS_PROXY_SENDMSG_THREAD_NUM);
        if (StringUtils.isNotEmpty(proxyServerSendMsgThreadNumStr) && StringUtils.isNumeric(proxyServerSendMsgThreadNumStr)) {
            proxyServerSendMsgThreadNum = Integer.valueOf(StringUtils.deleteWhitespace(proxyServerSendMsgThreadNumStr));
        }

        String proxyServerReplyMsgThreadNumStr = configurationWraper.getProp(ConfKeys.KEYS_PROXY_REPLYMSG_THREAD_NUM);
        if (StringUtils.isNotEmpty(proxyServerReplyMsgThreadNumStr) && StringUtils.isNumeric(proxyServerReplyMsgThreadNumStr)) {
            proxyServerReplyMsgThreadNum = Integer.valueOf(StringUtils.deleteWhitespace(proxyServerReplyMsgThreadNumStr));
        }

        String proxyServerPushMsgThreadNumStr = configurationWraper.getProp(ConfKeys.KEYS_PROXY_PUSHMSG_THREAD_NUM);
        if (StringUtils.isNotEmpty(proxyServerPushMsgThreadNumStr) && StringUtils.isNumeric(proxyServerPushMsgThreadNumStr)) {
            proxyServerPushMsgThreadNum = Integer.valueOf(StringUtils.deleteWhitespace(proxyServerPushMsgThreadNumStr));
        }

        String proxyServerRegistryThreadNumStr = configurationWraper.getProp(ConfKeys.KEYS_PROXY_REGISTRY_THREAD_NUM);
        if (StringUtils.isNotEmpty(proxyServerRegistryThreadNumStr) && StringUtils.isNumeric(proxyServerRegistryThreadNumStr)) {
            proxyServerRegistryThreadNum = Integer.valueOf(StringUtils.deleteWhitespace(proxyServerRegistryThreadNumStr));
        }

        String proxyServerClientManageThreadNumStr = configurationWraper.getProp(ConfKeys.KEYS_PROXY_CLIENTMANAGE_THREAD_NUM);
        if (StringUtils.isNotEmpty(proxyServerClientManageThreadNumStr) && StringUtils.isNumeric(proxyServerClientManageThreadNumStr)) {
            proxyServerClientManageThreadNum = Integer.valueOf(StringUtils.deleteWhitespace(proxyServerClientManageThreadNumStr));
        }

        String proxyServerPullRegistryIntervelStr = configurationWraper.getProp(ConfKeys.KEYS_PROXY_PULL_REGISTRY_INTERVEL);
        if (StringUtils.isNotEmpty(proxyServerPullRegistryIntervelStr) && StringUtils.isNumeric(proxyServerPullRegistryIntervelStr)) {
            proxyServerPullRegistryIntervel = Integer.valueOf(StringUtils.deleteWhitespace(proxyServerPullRegistryIntervelStr));
        }

        String proxyServerAdminThreadNumStr = configurationWraper.getProp(ConfKeys.KEYS_PROXY_ADMIN_THREAD_NUM);
        if (StringUtils.isNotEmpty(proxyServerAdminThreadNumStr) && StringUtils.isNumeric(proxyServerAdminThreadNumStr)) {
            proxyServerAdminThreadNum = Integer.valueOf(StringUtils.deleteWhitespace(proxyServerAdminThreadNumStr));
        }

        String proxyServerRetryBlockQSizeStr = configurationWraper.getProp(ConfKeys.KEY_PROXY_RETRY_BLOCKQ_SIZE);
        if (StringUtils.isNotEmpty(proxyServerRetryBlockQSizeStr) && StringUtils.isNumeric(proxyServerRetryBlockQSizeStr)) {
            proxyServerRetryBlockQSize = Integer.valueOf(StringUtils.deleteWhitespace(proxyServerRetryBlockQSizeStr));
        }

        String proxyServerBatchBlockQSizeStr = configurationWraper.getProp(ConfKeys.KEY_PROXY_BATCHMSG_BLOCKQ_SIZE);
        if (StringUtils.isNotEmpty(proxyServerBatchBlockQSizeStr) && StringUtils.isNumeric(proxyServerBatchBlockQSizeStr)) {
            proxyServerBatchBlockQSize = Integer.valueOf(StringUtils.deleteWhitespace(proxyServerBatchBlockQSizeStr));
        }

        String proxyServerSendMsgBlockQSizeStr = configurationWraper.getProp(ConfKeys.KEY_PROXY_SENDMSG_BLOCKQ_SIZE);
        if (StringUtils.isNotEmpty(proxyServerSendMsgBlockQSizeStr) && StringUtils.isNumeric(proxyServerSendMsgBlockQSizeStr)) {
            proxyServerSendMsgBlockQSize = Integer.valueOf(StringUtils.deleteWhitespace(proxyServerSendMsgBlockQSizeStr));
        }

        String proxyServerPushMsgBlockQSizeStr = configurationWraper.getProp(ConfKeys.KEY_PROXY_PUSHMSG_BLOCKQ_SIZE);
        if (StringUtils.isNotEmpty(proxyServerPushMsgBlockQSizeStr) && StringUtils.isNumeric(proxyServerPushMsgBlockQSizeStr)) {
            proxyServerPushMsgBlockQSize = Integer.valueOf(StringUtils.deleteWhitespace(proxyServerPushMsgBlockQSizeStr));
        }

        String proxyServerClientManageBlockQSizeStr = configurationWraper.getProp(ConfKeys.KEY_PROXY_CLIENTM_BLOCKQ_SIZE);
        if (StringUtils.isNotEmpty(proxyServerClientManageBlockQSizeStr) && StringUtils.isNumeric(proxyServerClientManageBlockQSizeStr)) {
            proxyServerClientManageBlockQSize = Integer.valueOf(StringUtils.deleteWhitespace(proxyServerClientManageBlockQSizeStr));
        }

        String proxyServerBusyCheckIntervelStr = configurationWraper.getProp(ConfKeys.KEY_PROXY_BUSY_CHECK_INTERVEL);
        if (StringUtils.isNotEmpty(proxyServerBusyCheckIntervelStr) && StringUtils.isNumeric(proxyServerBusyCheckIntervelStr)) {
            proxyServerBusyCheckIntervel = Integer.valueOf(StringUtils.deleteWhitespace(proxyServerBusyCheckIntervelStr));
        }

        String proxyServerConsumerEnabledStr = configurationWraper.getProp(ConfKeys.KEY_PROXY_CONSUMER_ENABLED);
        if (StringUtils.isNotEmpty(proxyServerConsumerEnabledStr)) {
            proxyServerConsumerEnabled = Boolean.valueOf(StringUtils.deleteWhitespace(proxyServerConsumerEnabledStr));
        }

        String proxyServerRetryThreadNumStr = configurationWraper.getProp(ConfKeys.KEY_PROXY_RETRY_THREAD_NUM);
        if (StringUtils.isNotEmpty(proxyServerRetryThreadNumStr) && StringUtils.isNumeric(proxyServerRetryThreadNumStr)) {
            proxyServerRetryThreadNum = Integer.valueOf(StringUtils.deleteWhitespace(proxyServerRetryThreadNumStr));
        }

        String proxyServerUseTlsStr = configurationWraper.getProp(ConfKeys.KEY_PROXY_HTTPS_ENABLED);
        if (StringUtils.isNotEmpty(proxyServerUseTlsStr)) {
            proxyServerUseTls = Boolean.valueOf(StringUtils.deleteWhitespace(proxyServerUseTlsStr));
        }
    }

    static class ConfKeys{

        public static String KEYS_PROXY_SERVER_HTTP_PORT = "proxy.server.http.port";

        public static String KEYS_PROXY_BATCHMSG_THREAD_NUM = "proxy.server.batchmsg.threads.num";

        public static String KEYS_PROXY_BATCHMSG_RATELIMITER = "proxy.server.batchmsg.speed.ratelimiter";

        public static String KEYS_PROXY_BATCHMSG_BATCH_ENABLED = "proxy.server.batchmsg.batch.enabled";

        public static String KEYS_PROXY_ASYNC_ACCUMULATION_THRESHOLD = "proxy.server.async.accumulation.threshold";

        public static String KEY_PROXY_BUSY_CHECK_INTERVEL = "proxy.server.busy.check.intervel";

        public static String KEYS_PROXY_SENDMSG_THREAD_NUM = "proxy.server.sendmsg.threads.num";

        public static String KEYS_PROXY_REPLYMSG_THREAD_NUM = "proxy.server.replymsg.threads.num";

        public static String KEYS_PROXY_PUSHMSG_THREAD_NUM = "proxy.server.pushmsg.threads.num";

        public static String KEYS_PROXY_REGISTRY_THREAD_NUM = "proxy.server.registry.threads.num";

        public static String KEYS_PROXY_CLIENTMANAGE_THREAD_NUM = "proxy.server.clientmanage.threads.num";

        public static String KEYS_PROXY_ADMIN_THREAD_NUM = "proxy.server.admin.threads.num";

        public static String KEY_PROXY_RETRY_THREAD_NUM = "proxy.server.retry.threads.num";

        public static String KEYS_PROXY_PULL_REGISTRY_INTERVEL = "proxy.server.pull.registry.intervel";

        public static String KEY_PROXY_RETRY_BLOCKQ_SIZE = "proxy.server.retry.blockQ.size";

        public static String KEY_PROXY_BATCHMSG_BLOCKQ_SIZE = "proxy.server.batchmsg.blockQ.size";

        public static String KEY_PROXY_SENDMSG_BLOCKQ_SIZE = "proxy.server.sendmsg.blockQ.size";

        public static String KEY_PROXY_PUSHMSG_BLOCKQ_SIZE = "proxy.server.pushmsg.blockQ.size";

        public static String KEY_PROXY_CLIENTM_BLOCKQ_SIZE = "proxy.server.clientM.blockQ.size";

        public static String KEY_PROXY_CONSUMER_ENABLED = "proxy.server.consumer.enabled";

        public static String KEY_PROXY_HTTPS_ENABLED = "proxy.server.useTls.enabled";
    }
}
