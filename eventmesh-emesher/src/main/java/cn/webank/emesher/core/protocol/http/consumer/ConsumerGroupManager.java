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

package cn.webank.emesher.core.protocol.http.consumer;

import cn.webank.emesher.boot.ProxyHTTPServer;
import cn.webank.emesher.core.consumergroup.ConsumerGroupConf;

import java.util.concurrent.atomic.AtomicBoolean;

public class ConsumerGroupManager {

    protected AtomicBoolean started = new AtomicBoolean(Boolean.FALSE);

    protected AtomicBoolean inited = new AtomicBoolean(Boolean.FALSE);

    private ProxyHTTPServer proxyHTTPServer;

    private ProxyConsumer proxyConsumer;

    private ConsumerGroupConf consumerGroupConfig;

    public ConsumerGroupManager(ProxyHTTPServer proxyHTTPServer, ConsumerGroupConf consumerGroupConfig) {
        this.proxyHTTPServer = proxyHTTPServer;
        this.consumerGroupConfig = consumerGroupConfig;
        proxyConsumer = new ProxyConsumer(this.proxyHTTPServer, this.consumerGroupConfig);
    }

    public synchronized void init() throws Exception {
        proxyConsumer.init();
        inited.compareAndSet(false, true);
    }

    public synchronized void start() throws Exception {
        steupProxyConsumer(consumerGroupConfig);
        proxyConsumer.start();
        started.compareAndSet(false, true);
    }

    private synchronized void steupProxyConsumer(ConsumerGroupConf consumerGroupConfig) throws Exception {
        for(String topic:consumerGroupConfig.getConsumerGroupTopicConf().keySet()) {
            proxyConsumer.subscribe(topic);
        }
    }

    public synchronized void shutdown() throws Exception {
        proxyConsumer.shutdown();
        started.compareAndSet(true, false);
    }

    public synchronized void refresh(ConsumerGroupConf consumerGroupConfig) throws Exception {

        if(consumerGroupConfig == null || this.consumerGroupConfig.equals(consumerGroupConfig)) {
            return;
        }

        if(started.get()) {
            shutdown();
        }

        this.consumerGroupConfig = consumerGroupConfig;
        init();
        start();
    }

    public ConsumerGroupConf getConsumerGroupConfig() {
        return consumerGroupConfig;
    }
}
