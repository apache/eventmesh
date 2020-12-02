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

package com.webank.emesher.core.plugin.impl;

import com.webank.defibus.client.impl.producer.RRCallback;
import com.webank.emesher.configuration.CommonConfiguration;
import com.webank.emesher.constants.ProxyConstants;
import com.webank.eventmesh.common.ThreadUtil;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageClientIDSetter;
import org.apache.rocketmq.remoting.exception.RemotingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RocketMQProducerImpl implements MeshMQProducer {

    public Logger logger = LoggerFactory.getLogger(this.getClass());


    protected DefaultMQProducer defaultMQProducer;

    @Override
    public synchronized void init(CommonConfiguration commonConfiguration, String producerGroup) {

        defaultMQProducer = new DefaultMQProducer(ProxyConstants.PRODUCER_GROUP_NAME_PREFIX + producerGroup);
        defaultMQProducer.setHeartbeatBrokerInterval(commonConfiguration.heartbeatBrokerInterval);
        defaultMQProducer.setPollNameServerInterval(commonConfiguration.pollNameServerInteval);
        defaultMQProducer.setNamesrvAddr(commonConfiguration.namesrvAddr);
        MessageClientIDSetter.createUniqID();
        defaultMQProducer.setVipChannelEnabled(false);
        defaultMQProducer.setCompressMsgBodyOverHowmuch(2 * 1024);

    }

    @Override
    public synchronized void start() throws Exception {

        defaultMQProducer.start();
        ThreadUtil.randomSleep(500);
        defaultMQProducer.getDefaultMQProducerImpl().getmQClientFactory().updateTopicRouteInfoFromNameServer();

    }

    @Override
    public synchronized void shutdown() throws Exception {

        defaultMQProducer.shutdown();
    }

    @Override
    public void send(Message message, SendCallback sendCallback) throws Exception {
        defaultMQProducer.send(message, sendCallback);
    }

    @Override
    public void request(Message message, SendCallback sendCallback, RRCallback rrCallback, long timeout)
            throws InterruptedException, RemotingException, MQClientException, MQBrokerException {
        throw new UnsupportedOperationException("not support request-reply mode when eventstore=rocketmq");
    }

    @Override
    public Message request(Message message, long timeout) throws InterruptedException, RemotingException, MQClientException, MQBrokerException {
        throw new UnsupportedOperationException("not support request-reply mode when eventstore=rocketmq");
    }

    @Override
    public boolean reply(final Message message, final SendCallback sendCallback) throws Exception {
        throw new UnsupportedOperationException("not support request-reply mode when eventstore=rocketmq");
    }

    @Override
    public DefaultMQProducer getDefaultMQProducer() {
        return defaultMQProducer;
    }
}
