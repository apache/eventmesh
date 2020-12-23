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

package com.webank.eventmesh.connector.defibus.producer;

import com.webank.eventmesh.api.RRCallback;
import com.webank.eventmesh.api.SendCallback;
import com.webank.eventmesh.api.producer.MeshMQProducer;
import com.webank.defibus.client.common.DeFiBusClientConfig;
import com.webank.defibus.producer.DeFiBusProducer;
import com.webank.eventmesh.common.ThreadUtil;
import com.webank.eventmesh.connector.defibus.common.Constants;
import com.webank.eventmesh.connector.defibus.common.ProxyConstants;
import com.webank.eventmesh.connector.defibus.config.ClientConfiguration;
import com.webank.eventmesh.connector.defibus.config.ConfigurationWraper;
import com.webank.eventmesh.connector.defibus.utils.OMSUtil;
import io.openmessaging.BytesMessage;
import io.openmessaging.Future;
import io.openmessaging.KeyValue;
import io.openmessaging.Message;
import io.openmessaging.exception.OMSNotSupportedException;
import io.openmessaging.interceptor.ProducerInterceptor;
import io.openmessaging.producer.BatchMessageSender;
import io.openmessaging.producer.LocalTransactionExecutor;
import io.openmessaging.producer.SendResult;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.message.MessageClientIDSetter;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.remoting.exception.RemotingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;

import static com.webank.eventmesh.connector.defibus.utils.OMSUtil.msgConvert;

public class DeFiBusProducerImpl implements MeshMQProducer {


    public Logger logger = LoggerFactory.getLogger(this.getClass());

    protected DeFiBusProducer defibusProducer;

    @Override
    public synchronized void init(KeyValue keyValue) {
        ConfigurationWraper configurationWraper =
                new ConfigurationWraper(ProxyConstants.PROXY_CONF_HOME
                        + File.separator
                        + ProxyConstants.PROXY_CONF_FILE, false);
        final ClientConfiguration clientConfiguration = new ClientConfiguration(configurationWraper);
        clientConfiguration.init();

        String producerGroup = keyValue.getString("producerGroup");
        String proxyIDC = keyValue.getString("proxyIDC");
        String instanceName = keyValue.getString("instanceName");

        DeFiBusClientConfig wcc = new DeFiBusClientConfig();
        wcc.setNamesrvAddr(clientConfiguration.namesrvAddr);
        wcc.setPollNameServerInterval(clientConfiguration.pollNameServerInteval);
        wcc.setHeartbeatBrokerInterval(clientConfiguration.heartbeatBrokerInterval);
        wcc.setProducerGroup(Constants.PRODUCER_GROUP_NAME_PREFIX + producerGroup);
        wcc.setClusterPrefix(proxyIDC);

        MessageClientIDSetter.createUniqID();
        defibusProducer = new DeFiBusProducer(wcc);
        defibusProducer.getDefaultMQProducer().setVipChannelEnabled(false);
        defibusProducer.getDefaultMQProducer().setCompressMsgBodyOverHowmuch(2 * 1024);

        //set instance name
        defibusProducer.getDefaultMQProducer().setInstanceName(instanceName);
    }

    @Override
    public synchronized void start() throws Exception {

        defibusProducer.start();
        ThreadUtil.randomSleep(500);
        defibusProducer.getDefaultMQProducer().getDefaultMQProducerImpl().getmQClientFactory().updateTopicRouteInfoFromNameServer();

    }

    @Override
    public void startup() {

    }

    //TODO void shutdown() throws Exception;
    @Override
    public synchronized void shutdown() {

        defibusProducer.shutdown();

    }

    @Override
    public void send(Message message, SendCallback sendCallback) throws Exception {
        checkMessageType(message);
        org.apache.rocketmq.common.message.Message rmqMessage = msgConvert((BytesMessage) message);
        defibusProducer.publish(rmqMessage, createRocSendCallback(sendCallback));
    }

    @Override
    public void request(Message message, SendCallback sendCallback, RRCallback rrCallback, long timeout)
            throws InterruptedException, RemotingException, MQClientException, MQBrokerException {
        checkMessageType(message);
        org.apache.rocketmq.common.message.Message rmqMessage = msgConvert((BytesMessage) message);
        defibusProducer.request(rmqMessage, createRocSendCallback(sendCallback), createDefibusRRCallback(rrCallback), timeout);
    }

    @Override
    public Message request(Message message, long timeout) throws Exception {
        checkMessageType(message);
        org.apache.rocketmq.common.message.Message rmqMessage = msgConvert((BytesMessage) message);
        return msgConvert(defibusProducer.request(rmqMessage, timeout));
    }

    @Override
    public boolean reply(final Message message, final SendCallback sendCallback) throws Exception {
        checkMessageType(message);
        org.apache.rocketmq.common.message.Message rmqMessage = msgConvert((BytesMessage) message);
        defibusProducer.reply(rmqMessage, createRocSendCallback(sendCallback));
        return true;
    }

    @Override
    public String buildMQClientId() {
        return defibusProducer.getDefaultMQProducer().buildMQClientId();
    }

    @Override
    public void setExtFields() {

    }

    @Override
    public void getDefaultTopicRouteInfoFromNameServer(String topic, final long timeout) throws Exception{
        defibusProducer.getDefaultMQProducer().getDefaultMQProducerImpl()
                .getmQClientFactory().getMQClientAPIImpl().getDefaultTopicRouteInfoFromNameServer(topic,
                timeout);
    }

    @Override
    public MeshMQProducer getMeshMQProducer(){
        return this;
    }

    @Override
    public KeyValue attributes() {
        return null;
    }

    @Override
    public SendResult send(io.openmessaging.Message message) {
        return null;
    }

    @Override
    public SendResult send(io.openmessaging.Message message, KeyValue attributes) {
        return null;
    }

    @Override
    public SendResult send(io.openmessaging.Message message, LocalTransactionExecutor branchExecutor, KeyValue attributes) {
        return null;
    }

    @Override
    public Future<SendResult> sendAsync(io.openmessaging.Message message) {
        return null;
    }

    @Override
    public Future<SendResult> sendAsync(io.openmessaging.Message message, KeyValue attributes) {
        return null;
    }

    @Override
    public void sendOneway(io.openmessaging.Message message) {

    }

    @Override
    public void sendOneway(io.openmessaging.Message message, KeyValue properties) {

    }

    @Override
    public BatchMessageSender createBatchMessageSender() {
        return null;
    }

    @Override
    public void addInterceptor(ProducerInterceptor interceptor) {

    }

    @Override
    public void removeInterceptor(ProducerInterceptor interceptor) {

    }

    @Override
    public BytesMessage createBytesMessage(String queue, byte[] body) {
        return null;
    }

    private void checkMessageType(Message message) {
        if (!(message instanceof BytesMessage)) {
            throw new OMSNotSupportedException("-1", "Only BytesMessage is supported.");
        }
    }

    private org.apache.rocketmq.client.producer.SendCallback createRocSendCallback(final SendCallback sendCallback){
        return new org.apache.rocketmq.client.producer.SendCallback() {
            @Override
            public void onSuccess(final org.apache.rocketmq.client.producer.SendResult sendResult) {

                sendCallback.onSuccess(new SendResult() {
                    @Override
                    public String messageId() {
                        return sendResult.getMsgId();
                    }
                });
            }

            @Override
            public void onException(Throwable e) {
                sendCallback.onException(e);
            }
        };
    }

    private com.webank.defibus.client.impl.producer.RRCallback createDefibusRRCallback(final RRCallback rrCallback){
        return new com.webank.defibus.client.impl.producer.RRCallback(){

            @Override
            public void onSuccess(org.apache.rocketmq.common.message.Message msg) {
                BytesMessage omsMsg = null;
                if (msg instanceof MessageExt) {
                    omsMsg = OMSUtil.msgConvert((MessageExt)msg);
                }else{
                    omsMsg = OMSUtil.msgConvert(msg);
                }
                rrCallback.onSuccess(omsMsg);
            }

            @Override
            public void onException(Throwable e) {
                rrCallback.onException(e);
            }
        };
    }
}
