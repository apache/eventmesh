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

package cn.webank.eventmesh.client.http.consumer;

import cn.webank.eventmesh.client.http.AbstractLiteClient;
import cn.webank.eventmesh.client.http.RemotingServer;
import cn.webank.eventmesh.client.http.conf.LiteClientConfig;
import cn.webank.eventmesh.client.http.consumer.listener.LiteMessageListener;
import cn.webank.eventmesh.common.ProxyException;
import cn.webank.eventmesh.common.ThreadPoolFactory;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;

public class LiteConsumer extends AbstractLiteClient {

    public Logger logger = LoggerFactory.getLogger(LiteConsumer.class);

    private RemotingServer remotingServer;

    private ThreadPoolExecutor consumeExecutor;

    private static CloseableHttpClient httpClient = HttpClients.createDefault();

    protected LiteClientConfig weMQProxyClientConfig;

    private List<String> subscription = Lists.newArrayList();

    private LiteMessageListener messageListener;

    public LiteConsumer(LiteClientConfig liteClientConfig) {
        super(liteClientConfig);
        this.consumeExecutor = ThreadPoolFactory.createThreadPoolExecutor(weMQProxyClientConfig.getConsumeThreadCore(),
                weMQProxyClientConfig.getConsumeThreadMax(), "proxy-client-consume-");
        this.remotingServer = new RemotingServer(consumeExecutor);
    }

    public LiteConsumer(LiteClientConfig liteClientConfig,
                        ThreadPoolExecutor customExecutor) {
        super(liteClientConfig);
        this.consumeExecutor = customExecutor;
        this.remotingServer = new RemotingServer(this.consumeExecutor);
    }

    private AtomicBoolean started = new AtomicBoolean(Boolean.FALSE);

    public void start() throws ProxyException {
        Preconditions.checkState(weMQProxyClientConfig != null, "weMQProxyClientConfig can't be null");
        Preconditions.checkState(consumeExecutor != null, "consumeExecutor can't be null");
        Preconditions.checkState(messageListener != null, "messageListener can't be null");
        logger.info("LiteConsumer starting");
        super.start();
        started.compareAndSet(false, true);
        logger.info("LiteConsumer started");
    }

    public void shutdown() throws Exception {
        logger.info("LiteConsumer shutting down");
        super.shutdown();
        httpClient.close();
        started.compareAndSet(true, false);
        logger.info("LiteConsumer shutdown");
    }


    public boolean subscribe(String topic) throws ProxyException {
        subscription.add(topic);
        //做一次心跳
        return Boolean.TRUE;
    }

    public boolean unsubscribe(String topic) throws ProxyException {
        subscription.remove(topic);
        //做一次心跳
        return Boolean.TRUE;
    }

    public void registerMessageListener(LiteMessageListener messageListener) throws ProxyException {
        this.messageListener = messageListener;
        remotingServer.registerMessageListener(this.messageListener);
    }
}
