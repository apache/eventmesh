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

import com.webank.eventmesh.connector.rocketmq.utils.BeanUtils;
import com.webank.eventmesh.connector.rocketmq.utils.OMSUtil;
import com.webank.eventmesh.connector.rocketmq.config.ClientConfig;
import io.openmessaging.api.exception.OMSRuntimeException;
import io.openmessaging.api.exception.OMSMessageFormatException;
import io.openmessaging.api.exception.OMSTimeOutException;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.impl.producer.DefaultMQProducerImpl;
import org.apache.rocketmq.client.log.ClientLogger;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.protocol.ResponseCode;
import org.apache.rocketmq.logging.InternalLogger;
import org.apache.rocketmq.remoting.exception.RemotingConnectException;
import org.apache.rocketmq.remoting.exception.RemotingTimeoutException;
import org.apache.rocketmq.remoting.protocol.LanguageCode;

import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class AbstractOMSProducer {

    final static InternalLogger log = ClientLogger.getLog();
    final Properties properties;
    final DefaultMQProducer rocketmqProducer;
    protected final AtomicBoolean started = new AtomicBoolean(false);
//    private boolean started = false;
    private final ClientConfig clientConfig;
    private final String PRODUCER_ID = "PRODUCER_ID";

    AbstractOMSProducer(final Properties properties) {
        this.properties = properties;
        this.rocketmqProducer = new DefaultMQProducer();
        this.clientConfig = BeanUtils.populate(properties, ClientConfig.class);

        String accessPoints = clientConfig.getAccessPoints();
        if (accessPoints == null || accessPoints.isEmpty()) {
            throw new OMSRuntimeException(-1, "OMS AccessPoints is null or empty.");
        }

        this.rocketmqProducer.setNamesrvAddr(accessPoints.replace(',', ';'));

        this.rocketmqProducer.setProducerGroup(clientConfig.getRmqProducerGroup());

        String producerId = OMSUtil.buildInstanceName();
        this.rocketmqProducer.setSendMsgTimeout(clientConfig.getOperationTimeout());
        this.rocketmqProducer.setInstanceName(producerId);
        this.rocketmqProducer.setMaxMessageSize(1024 * 1024 * 4);
        this.rocketmqProducer.setLanguage(LanguageCode.OMS);
        properties.put(PRODUCER_ID, producerId);
    }

    public synchronized void start() {
        if (!started.get()) {
            try {
                this.rocketmqProducer.start();
            } catch (MQClientException e) {
                throw new OMSRuntimeException("-1", e);
            }
        }
        this.started.set(true);
    }

    public synchronized void shutdown() {
        if (this.started.get()) {
            this.rocketmqProducer.shutdown();
        }
        this.started.set(false);
    }

    public boolean isStarted() {
        return this.started.get();
    }

    public boolean isClosed() {
        return !this.isStarted();
    }

    OMSRuntimeException checkProducerException(String topic, String msgId, Throwable e) {
        if (e instanceof MQClientException) {
            if (e.getCause() != null) {
                if (e.getCause() instanceof RemotingTimeoutException) {
                    return new OMSTimeOutException(-1, String.format("Send message to broker timeout, %dms, Topic=%s, msgId=%s",
                        this.rocketmqProducer.getSendMsgTimeout(), topic, msgId), e);
                } else if (e.getCause() instanceof MQBrokerException || e.getCause() instanceof RemotingConnectException) {
                    if (e.getCause() instanceof MQBrokerException) {
                        MQBrokerException brokerException = (MQBrokerException) e.getCause();
                        return new OMSRuntimeException(-1, String.format("Received a broker exception, Topic=%s, msgId=%s, %s",
                            topic, msgId, brokerException.getErrorMessage()), e);
                    }

                    if (e.getCause() instanceof RemotingConnectException) {
                        RemotingConnectException connectException = (RemotingConnectException)e.getCause();
                        return new OMSRuntimeException(-1,
                            String.format("Network connection experiences failures. Topic=%s, msgId=%s, %s",
                                topic, msgId, connectException.getMessage()),
                            e);
                    }
                }
            }
            // Exception thrown by local.
            else {
                MQClientException clientException = (MQClientException) e;
                if (-1 == clientException.getResponseCode()) {
                    return new OMSRuntimeException(-1, String.format("Topic does not exist, Topic=%s, msgId=%s",
                        topic, msgId), e);
                } else if (ResponseCode.MESSAGE_ILLEGAL == clientException.getResponseCode()) {
                    return new OMSMessageFormatException(-1, String.format("A illegal message for RocketMQ, Topic=%s, msgId=%s",
                        topic, msgId), e);
                }
            }
        }
        return new OMSRuntimeException(-1, "Send message to RocketMQ broker failed.", e);
    }

    protected void checkProducerServiceState(DefaultMQProducerImpl producer) {
        switch(producer.getServiceState()) {
            case CREATE_JUST:
                throw new OMSRuntimeException(String.format("You do not have start the producer, %s", producer.getServiceState()));
            case SHUTDOWN_ALREADY:
                throw new OMSRuntimeException(String.format("Your producer has been shut down, %s", producer.getServiceState()));
            case START_FAILED:
                throw new OMSRuntimeException(String.format("When you start your service throws an exception, %s", producer.getServiceState()));
            case RUNNING:
            default:
        }
    }

    public DefaultMQProducer getRocketmqProducer() {
        return rocketmqProducer;
    }
}
