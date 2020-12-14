///*
// * Licensed to the Apache Software Foundation (ASF) under one or more
// * contributor license agreements.  See the NOTICE file distributed with
// * this work for additional information regarding copyright ownership.
// * The ASF licenses this file to You under the Apache License, Version 2.0
// * (the "License"); you may not use this file except in compliance with
// * the License.  You may obtain a copy of the License at
// *
// *     http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//
//package com.webank.runtime.core.plugin.impl;
//
//import com.webank.defibus.client.common.DeFiBusClientConfig;
//import com.webank.defibus.client.impl.producer.RRCallback;
//import com.webank.defibus.producer.DeFiBusProducer;
//import com.webank.runtime.configuration.CommonConfiguration;
//import com.webank.runtime.constants.ProxyConstants;
//import com.webank.eventmesh.common.ThreadUtil;
//import org.apache.rocketmq.client.exception.MQBrokerException;
//import org.apache.rocketmq.client.exception.MQClientException;
//import org.apache.rocketmq.client.producer.DefaultMQProducer;
//import org.apache.rocketmq.client.producer.SendCallback;
//import org.apache.rocketmq.common.message.Message;
//import org.apache.rocketmq.common.message.MessageClientIDSetter;
//import org.apache.rocketmq.remoting.exception.RemotingException;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//public class DeFiBusProducerImpl implements MeshMQProducer {
//
//    public Logger logger = LoggerFactory.getLogger(this.getClass());
//
//    protected DeFiBusProducer defibusProducer;
//
//    @Override
//    public synchronized void init(CommonConfiguration commonConfiguration, String producerGroup) {
//
//        DeFiBusClientConfig wcc = new DeFiBusClientConfig();
//        wcc.setClusterPrefix(commonConfiguration.proxyIDC);
//        wcc.setPollNameServerInterval(commonConfiguration.pollNameServerInteval);
//        wcc.setHeartbeatBrokerInterval(commonConfiguration.heartbeatBrokerInterval);
//        wcc.setProducerGroup(ProxyConstants.PRODUCER_GROUP_NAME_PREFIX + producerGroup);
//        wcc.setNamesrvAddr(commonConfiguration.namesrvAddr);
//        MessageClientIDSetter.createUniqID();
//        defibusProducer = new DeFiBusProducer(wcc);
//        defibusProducer.getDefaultMQProducer().setVipChannelEnabled(false);
//        defibusProducer.getDefaultMQProducer().setCompressMsgBodyOverHowmuch(2 * 1024);
//
//    }
//
//    @Override
//    public synchronized void start() throws Exception {
//
//        defibusProducer.start();
//        ThreadUtil.randomSleep(500);
//        defibusProducer.getDefaultMQProducer().getDefaultMQProducerImpl().getmQClientFactory().updateTopicRouteInfoFromNameServer();
//
//    }
//
//    @Override
//    public synchronized void shutdown() throws Exception {
//
//        defibusProducer.shutdown();
//
//    }
//
//    @Override
//    public void send(Message message, SendCallback sendCallback) throws Exception {
//        defibusProducer.publish(message, sendCallback);
//    }
//
//    @Override
//    public void request(Message message, SendCallback sendCallback, RRCallback rrCallback, long timeout)
//            throws InterruptedException, RemotingException, MQClientException, MQBrokerException {
//
//        defibusProducer.request(message, sendCallback, rrCallback, timeout);
//    }
//
//    @Override
//    public Message request(Message message, long timeout) throws Exception {
//
//        return defibusProducer.request(message, timeout);
//    }
//
//    @Override
//    public boolean reply(final Message message, final SendCallback sendCallback) throws Exception {
//
//        defibusProducer.reply(message, sendCallback);
//        return true;
//    }
//
//    @Override
//    public DefaultMQProducer getDefaultMQProducer() {
//        return defibusProducer.getDefaultMQProducer();
//    }
//}
