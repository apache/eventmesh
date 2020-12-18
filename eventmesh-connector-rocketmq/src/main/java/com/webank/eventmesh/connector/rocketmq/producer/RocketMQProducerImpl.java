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

package com.webank.eventmesh.connector.rocketmq.producer;

import com.webank.eventmesh.api.RRCallback;
import com.webank.eventmesh.api.SendCallback;
import com.webank.eventmesh.api.producer.MeshMQProducer;
import com.webank.eventmesh.common.config.CommonConfiguration;
import io.openmessaging.*;
import io.openmessaging.interceptor.ProducerInterceptor;
import io.openmessaging.producer.BatchMessageSender;
import io.openmessaging.producer.LocalTransactionExecutor;
import io.openmessaging.producer.SendResult;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.remoting.exception.RemotingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RocketMQProducerImpl implements MeshMQProducer {

    public Logger logger = LoggerFactory.getLogger(this.getClass());

    private ProducerImpl producer;
    public final String DEFAULT_ACCESS_DRIVER = "com.webank.connector.rocketmq.MessagingAccessPointImpl";

    @Override
    public synchronized void init(CommonConfiguration commonConfiguration, String producerGroup) {
        String omsNamesrv = "oms:rocketmq://" + commonConfiguration.namesrvAddr + "/namespace";
        KeyValue properties = OMS.newKeyValue().put(OMSBuiltinKeys.DRIVER_IMPL, DEFAULT_ACCESS_DRIVER);
        properties.put("ACCESS_POINTS", omsNamesrv)
                .put("REGION", "namespace")
                .put(OMSBuiltinKeys.PRODUCER_ID, producerGroup)
                .put("RMQ_PRODUCER_GROUP", producerGroup)
                .put(OMSBuiltinKeys.OPERATION_TIMEOUT, 3000);
        MessagingAccessPoint messagingAccessPoint = OMS.getMessagingAccessPoint(omsNamesrv, properties);
        producer = (ProducerImpl) messagingAccessPoint.createProducer();

    }

    @Override
    public synchronized void start() throws Exception {

        producer.startup();
//        ThreadUtil.randomSleep(500);
//        defaultMQProducer.getDefaultMQProducerImpl().getmQClientFactory().updateTopicRouteInfoFromNameServer();

    }

    @Override
    public void startup() {
        producer.startup();
    }

    @Override
    public synchronized void shutdown() {

        producer.shutdown();
    }

    @Override
    public void send(Message message, SendCallback sendCallback) throws Exception {
        if (producer.getSendCallback() == null){
            producer.setSendCallback(sendCallback);
        }
        producer.sendAsync(message);
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
    public MeshMQProducer getDefaultMQProducer() {
        return this;
    }

    @Override
    public String buildMQClientId() {
        return producer.getRocketmqProducer().buildMQClientId();
    }

    @Override
    public void setExtFields() {
        producer.setExtFields();
    }

    @Override
    public void setInstanceName(String instanceName) {
        producer.getRocketmqProducer().setInstanceName(instanceName);
    }

    @Override
    public void getDefaultTopicRouteInfoFromNameServer(String topic, long timeout) throws Exception {
        producer.getRocketmqProducer().getDefaultMQProducerImpl()
                .getmQClientFactory().getMQClientAPIImpl().getDefaultTopicRouteInfoFromNameServer(topic,
                timeout);
    }

    @Override
    public KeyValue attributes() {
        return producer.attributes();
    }

    @Override
    public SendResult send(io.openmessaging.Message message) {
        return producer.send(message);
    }

    @Override
    public SendResult send(io.openmessaging.Message message, KeyValue attributes) {
        return producer.send(message, attributes);
    }

    @Override
    public SendResult send(io.openmessaging.Message message, LocalTransactionExecutor branchExecutor, KeyValue attributes) {
        return producer.send(message, branchExecutor, attributes);
    }

    @Override
    public Future<SendResult> sendAsync(io.openmessaging.Message message) {
        return producer.sendAsync(message);
    }

    @Override
    public Future<SendResult> sendAsync(io.openmessaging.Message message, KeyValue attributes) {
        return producer.sendAsync(message, attributes);
    }

    @Override
    public void sendOneway(io.openmessaging.Message message) {
        producer.sendOneway(message);
    }

    @Override
    public void sendOneway(io.openmessaging.Message message, KeyValue properties) {
        producer.sendOneway(message, properties);
    }

    @Override
    public BatchMessageSender createBatchMessageSender() {
        return producer.createBatchMessageSender();
    }

    @Override
    public void addInterceptor(ProducerInterceptor interceptor) {
        producer.addInterceptor(interceptor);
    }

    @Override
    public void removeInterceptor(ProducerInterceptor interceptor) {
        producer.removeInterceptor(interceptor);
    }

    @Override
    public BytesMessage createBytesMessage(String queue, byte[] body) {
        return producer.createBytesMessage(queue, body);
    }
}
