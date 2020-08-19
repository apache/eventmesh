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

package cn.webank.emesher.core.protocol.http.producer;

import cn.webank.defibus.client.common.DeFiBusClientConfig;
import cn.webank.defibus.client.impl.producer.RRCallback;
import cn.webank.defibus.producer.DeFiBusProducer;
import cn.webank.emesher.configuration.ProxyConfiguration;
import cn.webank.emesher.constants.ProxyConstants;
import cn.webank.emesher.core.consumergroup.ProducerGroupConf;
import cn.webank.eventmesh.common.ThreadUtil;
import cn.webank.emesher.util.ProxyUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageClientIDSetter;
import org.apache.rocketmq.remoting.exception.RemotingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

public class ProxyProducer {

    protected AtomicBoolean started = new AtomicBoolean(Boolean.FALSE);

    protected AtomicBoolean inited = new AtomicBoolean(Boolean.FALSE);

    public Logger logger = LoggerFactory.getLogger(this.getClass());

    public AtomicBoolean getInited() {
        return inited;
    }

    public AtomicBoolean getStarted() {
        return started;
    }

    protected ProducerGroupConf producerGroupConfig;

    protected ProxyConfiguration proxyConfiguration;

    public void send(SendMessageContext sendMsgContext, SendCallback sendCallback) throws Exception {
        defibusProducer.publish(sendMsgContext.getMsg(), sendCallback);
    }

    public void request(SendMessageContext sendMsgContext, SendCallback sendCallback, RRCallback rrCallback, long timeout)
            throws InterruptedException, RemotingException, MQClientException, MQBrokerException {
        defibusProducer.request(sendMsgContext.getMsg(), sendCallback, rrCallback, timeout);
    }

    public Message request(SendMessageContext sendMessageContext, long timeout) throws InterruptedException, RemotingException, MQClientException, MQBrokerException {
        return defibusProducer.request(sendMessageContext.getMsg(), timeout);
    }

    public boolean reply(final SendMessageContext sendMsgContext, final SendCallback sendCallback) throws Exception {
        defibusProducer.reply(sendMsgContext.getMsg(), sendCallback);
        return true;
    }

    protected DeFiBusProducer defibusProducer;

    public DeFiBusProducer getDefibusProducer() {
        return defibusProducer;
    }

    public synchronized void init(ProxyConfiguration proxyConfiguration, ProducerGroupConf producerGroupConfig) {
        this.producerGroupConfig = producerGroupConfig;
        this.proxyConfiguration = proxyConfiguration;
        DeFiBusClientConfig wcc = new DeFiBusClientConfig();
        wcc.setClusterPrefix(proxyConfiguration.proxyIDC);
        wcc.setPollNameServerInterval(proxyConfiguration.pollNameServerInteval);
        wcc.setHeartbeatBrokerInterval(proxyConfiguration.heartbeatBrokerInterval);
        wcc.setProducerGroup(ProxyConstants.PRODUCER_GROUP_NAME_PREFIX + producerGroupConfig.getGroupName());

        wcc.setNamesrvAddr(proxyConfiguration.namesrvAddr);

        MessageClientIDSetter.createUniqID();
        defibusProducer = new DeFiBusProducer(wcc);
        inited.compareAndSet(false, true);
        logger.info("ProxyProducer [{}] inited.............", producerGroupConfig.getGroupName());
    }


    public synchronized void start() throws Exception {
        if (started.get()) {
            return;
        }
        defibusProducer.getDefaultMQProducer().setVipChannelEnabled(false);
        defibusProducer.getDefaultMQProducer().setInstanceName(ProxyUtil.buildProxyClientID(producerGroupConfig.getGroupName(),
                proxyConfiguration.proxyRegion, proxyConfiguration.proxyCluster));
        defibusProducer.getDefaultMQProducer().setCompressMsgBodyOverHowmuch(2 * 1024);
        defibusProducer.start();
        started.compareAndSet(false, true);
        ThreadUtil.randomSleep(500);
        defibusProducer.getDefaultMQProducer().getDefaultMQProducerImpl().getmQClientFactory().updateTopicRouteInfoFromNameServer();
        logger.info("ProxyProducer [{}] started.............", producerGroupConfig.getGroupName());
    }

    public synchronized void shutdown() throws Exception {
        if (!inited.get()) {
            return;
        }

        if (!started.get()) {
            return;
        }
        defibusProducer.shutdown();
        inited.compareAndSet(true, false);
        started.compareAndSet(true, false);
        logger.info("ProxyProducer [{}] shutdown.............", producerGroupConfig.getGroupName());
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("proxyProducer={")
                .append("inited=").append(inited.get()).append(",")
                .append("started=").append(started.get()).append(",")
                .append("producerGroupConfig=").append(producerGroupConfig).append("}");
        return sb.toString();
    }
}
