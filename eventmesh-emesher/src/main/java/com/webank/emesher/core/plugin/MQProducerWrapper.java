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

package com.webank.emesher.core.plugin;

import com.webank.defibus.client.common.DeFiBusClientConfig;
import com.webank.defibus.client.impl.producer.RRCallback;
import com.webank.defibus.producer.DeFiBusProducer;
import com.webank.emesher.configuration.CommonConfiguration;
import com.webank.emesher.constants.ProxyConstants;
import com.webank.emesher.util.ProxyUtil;
import com.webank.eventmesh.common.ThreadUtil;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.exception.RequestTimeoutException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.RequestCallback;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.utils.MessageUtil;
import org.apache.rocketmq.common.MixAll;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageAccessor;
import org.apache.rocketmq.common.message.MessageClientIDSetter;
import org.apache.rocketmq.common.message.MessageConst;
import org.apache.rocketmq.remoting.exception.RemotingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MQProducerWrapper extends MQWrapper {

    public Logger logger = LoggerFactory.getLogger(this.getClass());

    protected DeFiBusProducer defibusProducer;

    protected DefaultMQProducer defaultMQProducer;

    public synchronized void init(CommonConfiguration commonConfiguration, String producerGroup) {
        if (inited.get()) {
            return;
        }

        if (useRocket) {
            defaultMQProducer = new DefaultMQProducer(ProxyConstants.PRODUCER_GROUP_NAME_PREFIX + producerGroup);
            defaultMQProducer.setHeartbeatBrokerInterval(commonConfiguration.heartbeatBrokerInterval);
            defaultMQProducer.setPollNameServerInterval(commonConfiguration.pollNameServerInteval);
            defaultMQProducer.setNamesrvAddr(commonConfiguration.namesrvAddr);
            MessageClientIDSetter.createUniqID();
            defaultMQProducer.setVipChannelEnabled(false);
            defaultMQProducer.setCompressMsgBodyOverHowmuch(2 * 1024);
        } else {
            DeFiBusClientConfig wcc = new DeFiBusClientConfig();
            wcc.setClusterPrefix(commonConfiguration.proxyIDC);
            wcc.setPollNameServerInterval(commonConfiguration.pollNameServerInteval);
            wcc.setHeartbeatBrokerInterval(commonConfiguration.heartbeatBrokerInterval);
            wcc.setProducerGroup(ProxyConstants.PRODUCER_GROUP_NAME_PREFIX + producerGroup);
            wcc.setNamesrvAddr(commonConfiguration.namesrvAddr);
            MessageClientIDSetter.createUniqID();
            defibusProducer = new DeFiBusProducer(wcc);
            defibusProducer.getDefaultMQProducer().setVipChannelEnabled(false);
            defibusProducer.getDefaultMQProducer().setCompressMsgBodyOverHowmuch(2 * 1024);
        }

        inited.compareAndSet(false, true);
    }

    public synchronized void start() throws Exception {
        if (started.get()) {
            return;
        }

        if (useRocket) {
            defaultMQProducer.start();
            ThreadUtil.randomSleep(500);
            defaultMQProducer.getDefaultMQProducerImpl().getmQClientFactory().updateTopicRouteInfoFromNameServer();
        } else {
            defibusProducer.start();
            ThreadUtil.randomSleep(500);
            defibusProducer.getDefaultMQProducer().getDefaultMQProducerImpl().getmQClientFactory().updateTopicRouteInfoFromNameServer();
        }

        started.compareAndSet(false, true);
    }

    public synchronized void shutdown() throws Exception {
        if (!inited.get()) {
            return;
        }

        if (!started.get()) {
            return;
        }

        if (useRocket) {
            defaultMQProducer.shutdown();
        } else {
            defibusProducer.shutdown();
        }

        inited.compareAndSet(true, false);
        started.compareAndSet(true, false);
    }

    public void send(Message message, SendCallback sendCallback) throws Exception {
        if (useRocket) {
            defaultMQProducer.send(message, sendCallback);
            return;
        }
        defibusProducer.publish(message, sendCallback);
    }

    public void request(Message message, SendCallback sendCallback, RRCallback rrCallback, long timeout)
            throws InterruptedException, RemotingException, MQClientException, MQBrokerException {
        if (useRocket) {
            defaultMQProducer.request(message, new RequestCallback() {
                @Override
                public void onSuccess(Message message) {
                    rrCallback.onSuccess(message);
                }

                @Override
                public void onException(Throwable e) {
                    rrCallback.onException(e);
                }
            }, timeout);
            return;
        }
        defibusProducer.request(message, sendCallback, rrCallback, timeout);
    }

    public Message request(Message message, long timeout) throws InterruptedException, RemotingException, MQClientException, MQBrokerException, RequestTimeoutException {
        if (useRocket) {
            return defaultMQProducer.request(message, timeout);
        }

        return defibusProducer.request(message, timeout);
    }

    public boolean reply(final Message message, final SendCallback sendCallback) throws Exception {
        if (useRocket) {
            defaultMQProducer.send(message, sendCallback);
            return true;
        }
        defibusProducer.reply(message, sendCallback);
        return true;
    }

    public DefaultMQProducer getDefaultMQProducer() {
        if (useRocket) {
            return defaultMQProducer;
        }

        return defibusProducer.getDefaultMQProducer();
    }
}
