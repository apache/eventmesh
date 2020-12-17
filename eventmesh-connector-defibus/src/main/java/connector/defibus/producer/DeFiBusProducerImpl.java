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

package connector.defibus.producer;

import com.webank.api.SendCallback;
import com.webank.api.producer.MeshMQProducer;
import com.webank.defibus.client.common.DeFiBusClientConfig;
import com.webank.defibus.client.impl.producer.RRCallback;
import com.webank.defibus.producer.DeFiBusProducer;
import com.webank.eventmesh.common.ThreadUtil;
import com.webank.eventmesh.common.config.CommonConfiguration;
import connector.defibus.common.Constants;
import io.openmessaging.BytesMessage;
import io.openmessaging.Future;
import io.openmessaging.KeyValue;
import io.openmessaging.Message;
import io.openmessaging.interceptor.ProducerInterceptor;
import io.openmessaging.producer.BatchMessageSender;
import io.openmessaging.producer.LocalTransactionExecutor;
import io.openmessaging.producer.SendResult;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.MessageClientIDSetter;
import org.apache.rocketmq.remoting.exception.RemotingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeFiBusProducerImpl implements MeshMQProducer {

    public Logger logger = LoggerFactory.getLogger(this.getClass());

    protected DeFiBusProducer defibusProducer;

    @Override
    public synchronized void init(CommonConfiguration commonConfiguration, String producerGroup) {

        DeFiBusClientConfig wcc = new DeFiBusClientConfig();
        wcc.setClusterPrefix(commonConfiguration.proxyIDC);
        wcc.setPollNameServerInterval(commonConfiguration.pollNameServerInteval);
        wcc.setHeartbeatBrokerInterval(commonConfiguration.heartbeatBrokerInterval);
        wcc.setProducerGroup(Constants.PRODUCER_GROUP_NAME_PREFIX + producerGroup);
        wcc.setNamesrvAddr(commonConfiguration.namesrvAddr);
        MessageClientIDSetter.createUniqID();
        defibusProducer = new DeFiBusProducer(wcc);
        defibusProducer.getDefaultMQProducer().setVipChannelEnabled(false);
        defibusProducer.getDefaultMQProducer().setCompressMsgBodyOverHowmuch(2 * 1024);

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
        defibusProducer.publish(message, sendCallback);
    }

    @Override
    public void request(Message message, SendCallback sendCallback, RRCallback rrCallback, long timeout)
            throws InterruptedException, RemotingException, MQClientException, MQBrokerException {

        defibusProducer.request(message, sendCallback, rrCallback, timeout);
    }

    @Override
    public Message request(Message message, long timeout) throws Exception {

        return defibusProducer.request(message, timeout);
    }

    @Override
    public boolean reply(final Message message, final SendCallback sendCallback) throws Exception {

        defibusProducer.reply(message, sendCallback);
        return true;
    }

    @Override
    public DefaultMQProducer getDefaultMQProducer() {
        return defibusProducer.getDefaultMQProducer();
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
}
