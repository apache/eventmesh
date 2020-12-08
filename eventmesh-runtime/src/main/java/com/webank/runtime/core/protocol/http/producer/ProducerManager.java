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

package com.webank.runtime.core.protocol.http.producer;

import com.webank.runtime.boot.ProxyHTTPServer;
import com.webank.runtime.core.consumergroup.ProducerGroupConf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;

public class ProducerManager {

    public Logger logger = LoggerFactory.getLogger(this.getClass());

    private ProxyHTTPServer proxyHTTPServer;

    private ConcurrentHashMap<String /** groupName*/, ProxyProducer> producerTable = new ConcurrentHashMap<String, ProxyProducer>();

    public ProducerManager(ProxyHTTPServer proxyHTTPServer) {
        this.proxyHTTPServer = proxyHTTPServer;
    }

    public void init() throws Exception {
        logger.info("producerManager inited......");
    }

    public void start() throws Exception {
        logger.info("producerManager started......");
    }

    public ProxyProducer getProxyProducer(String producerGroup) throws Exception {
        ProxyProducer proxyProducer = null;
        if (!producerTable.containsKey(producerGroup)) {
            synchronized (producerTable) {
                if (!producerTable.containsKey(producerGroup)) {
                    ProducerGroupConf producerGroupConfig = new ProducerGroupConf(producerGroup);
                    proxyProducer = createProxyProducer(producerGroupConfig);
                    proxyProducer.start();
                }
            }
        }

        proxyProducer = producerTable.get(producerGroup);

        if (!proxyProducer.getStarted().get()) {
            proxyProducer.start();
        }

        return proxyProducer;
    }

    public synchronized ProxyProducer createProxyProducer(ProducerGroupConf producerGroupConfig) throws Exception {
        if (producerTable.containsKey(producerGroupConfig.getGroupName())) {
            return producerTable.get(producerGroupConfig.getGroupName());
        }
        ProxyProducer proxyProducer = new ProxyProducer();
        proxyProducer.init(proxyHTTPServer.getProxyConfiguration(), producerGroupConfig);
        producerTable.put(producerGroupConfig.getGroupName(), proxyProducer);
        return proxyProducer;
    }

    public void shutdown() {
        for (ProxyProducer proxyProducer : producerTable.values()) {
            try {
                proxyProducer.shutdown();
            } catch (Exception ex) {
                logger.error("shutdown proxyProducer[{}] err", proxyProducer, ex);
            }
        }
        logger.info("producerManager shutdown......");
    }

    public ProxyHTTPServer getProxyHTTPServer() {
        return proxyHTTPServer;
    }

    public ConcurrentHashMap<String, ProxyProducer> getProducerTable() {
        return producerTable;
    }
}
