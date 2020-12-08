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

package connector.rocketmq.producer;

import com.webank.defibus.client.impl.producer.RRCallback;
import com.webank.runtime.configuration.CommonConfiguration;
import com.webank.runtime.core.plugin.impl.MeshMQProducer;
import com.webank.runtime.util.OMSUtil;
import io.openmessaging.KeyValue;
import io.openmessaging.MessagingAccessPoint;
import io.openmessaging.OMS;
import io.openmessaging.OMSBuiltinKeys;
import io.openmessaging.producer.Producer;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.remoting.exception.RemotingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RocketMQProducerImpl implements MeshMQProducer {

    public Logger logger = LoggerFactory.getLogger(this.getClass());


//    protected DefaultMQProducer defaultMQProducer;

    private Producer producer;

    public final String DEFAULT_ACCESS_DRIVER = "connector.rocketmq.MessagingAccessPointImpl";

    @Override
    public synchronized void init(CommonConfiguration commonConfiguration, String producerGroup) {

        KeyValue properties = OMS.newKeyValue().put(OMSBuiltinKeys.DRIVER_IMPL, DEFAULT_ACCESS_DRIVER);
        properties.put("ACCESS_POINTS", commonConfiguration.namesrvAddr)
                .put("REGION", "namespace")
                .put(OMSBuiltinKeys.PRODUCER_ID, producerGroup)
                .put("RMQ_PRODUCER_GROUP", producerGroup)
                .put(OMSBuiltinKeys.OPERATION_TIMEOUT, 3000);
        MessagingAccessPoint messagingAccessPoint = OMS.getMessagingAccessPoint(commonConfiguration.namesrvAddr, properties);
        producer = messagingAccessPoint.createProducer();

    }

    @Override
    public synchronized void start() throws Exception {

        producer.startup();
//        ThreadUtil.randomSleep(500);
//        defaultMQProducer.getDefaultMQProducerImpl().getmQClientFactory().updateTopicRouteInfoFromNameServer();

    }

    @Override
    public synchronized void shutdown() throws Exception {

        producer.shutdown();
    }

    @Override
    public void send(Message message, SendCallback sendCallback) throws Exception {
        producer.sendAsync(OMSUtil.msgConvert((MessageExt) message));
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
        return (DefaultMQProducer) producer;
    }
}
