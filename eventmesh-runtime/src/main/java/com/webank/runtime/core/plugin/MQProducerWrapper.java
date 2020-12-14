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

package com.webank.runtime.core.plugin;

import com.webank.api.producer.MeshMQProducer;
import com.webank.defibus.client.impl.producer.RRCallback;
import com.webank.eventmesh.common.config.CommonConfiguration;
import com.webank.eventmesh.common.config.ConfigurationWraper;
import com.webank.runtime.constants.ProxyConstants;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.remoting.exception.RemotingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ServiceLoader;

public class MQProducerWrapper extends MQWrapper {

    public Logger logger = LoggerFactory.getLogger(this.getClass());

    protected com.webank.api.producer.MeshMQProducer meshMQProducer;

    public synchronized void init(CommonConfiguration commonConfiguration, String producerGroup) throws Exception{
        if (inited.get()) {
            return;
        }

        meshMQProducer = getMeshMQProducer();
        if (meshMQProducer == null){
            logger.error("can't load the meshMQProducer plugin, please check.");
            throw new RuntimeException("doesn't load the meshMQProducer plugin, please check.");
        }
        meshMQProducer.init(commonConfiguration, producerGroup);

        inited.compareAndSet(false, true);
    }

    private MeshMQProducer getMeshMQProducer() {
        ServiceLoader<MeshMQProducer> meshMQProducerServiceLoader = ServiceLoader.load(MeshMQProducer.class);
        if (meshMQProducerServiceLoader.iterator().hasNext()){
            return meshMQProducerServiceLoader.iterator().next();
        }
        return null;
    }

    public synchronized void start() throws Exception {
        if (started.get()) {
            return;
        }

        meshMQProducer.start();

        started.compareAndSet(false, true);
    }

    public synchronized void shutdown() throws Exception {
        if (!inited.get()) {
            return;
        }

        if (!started.get()) {
            return;
        }

        meshMQProducer.shutdown();

        inited.compareAndSet(true, false);
        started.compareAndSet(true, false);
    }

    public void send(Message message, SendCallback sendCallback) throws Exception {
        meshMQProducer.send(message, sendCallback);
    }

    public void request(Message message, SendCallback sendCallback, RRCallback rrCallback, long timeout)
            throws InterruptedException, RemotingException, MQClientException, MQBrokerException {
        meshMQProducer.request(message, sendCallback, rrCallback, timeout);
    }

    public Message request(Message message, long timeout) throws Exception {
        return meshMQProducer.request(message, timeout);
    }

    public boolean reply(final Message message, final SendCallback sendCallback) throws Exception {
        return meshMQProducer.reply(message, sendCallback);
    }

    public DefaultMQProducer getDefaultMQProducer() {
        return meshMQProducer.getDefaultMQProducer();
    }

    public static void main(String[] args) throws Exception {

        MQProducerWrapper mqProducerWrapper = new MQProducerWrapper();
        CommonConfiguration commonConfiguration = new CommonConfiguration(new ConfigurationWraper(ProxyConstants.PROXY_CONF_HOME
                + File.separator
                + ProxyConstants.PROXY_CONF_FILE, false));
        commonConfiguration.init();
        mqProducerWrapper.init(commonConfiguration, "TEST");
    }
}
