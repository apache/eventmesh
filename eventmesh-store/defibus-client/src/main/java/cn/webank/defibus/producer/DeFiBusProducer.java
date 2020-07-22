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

package cn.webank.defibus.producer;

import cn.webank.defibus.client.DeFiBusClientManager;
import cn.webank.defibus.client.common.DeFiBusClientConfig;
import cn.webank.defibus.client.impl.DeFiBusClientAPIImpl;
import cn.webank.defibus.client.impl.factory.DeFiBusClientInstance;
import cn.webank.defibus.client.impl.hook.DeFiBusClientHookFactory;
import cn.webank.defibus.client.impl.producer.DeFiBusProducerImpl;
import cn.webank.defibus.client.impl.producer.RRCallback;
import cn.webank.defibus.common.DeFiBusConstant;
import cn.webank.defibus.common.DeFiBusVersion;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.common.MixAll;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageClientIDSetter;
import org.apache.rocketmq.remoting.RPCHook;
import org.apache.rocketmq.remoting.exception.RemotingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeFiBusProducer {
    private static final Logger LOG = LoggerFactory.getLogger(DeFiBusProducer.class);
    private DefaultMQProducer defaultMQProducer;
    private DeFiBusClientInstance deFiBusClientInstance;
    private RPCHook rpcHook;
    private AtomicBoolean isStart = new AtomicBoolean(false);
    private DeFiBusProducerImpl deFiBusProducerImpl;
    private DeFiBusClientConfig deFiBusClientConfig;

    static {
        System.setProperty("rocketmq.client.log.loadconfig", "false");
    }

    public DeFiBusProducer() {
        this(new DeFiBusClientConfig());
    }

    public DeFiBusProducer(DeFiBusClientConfig deFiBusClientConfig) {
        RPCHook rpcHookForAuth = DeFiBusClientHookFactory.createRPCHook(deFiBusClientConfig.getRpcHook());
        defaultMQProducer = new DefaultMQProducer(deFiBusClientConfig.getProducerGroup(), rpcHookForAuth);
        defaultMQProducer.setVipChannelEnabled(false);
        this.rpcHook = rpcHookForAuth;
        this.deFiBusClientConfig = deFiBusClientConfig;
    }

    /**
     * start the producer which will begin to connect with the broker. A producer MUST call this method before sending any message
     * If the producer has been already started, nothing will happen.
     * @throws MQClientException
     */
    public void start() throws MQClientException {
        if (isStart.compareAndSet(false, true)) {
            try {
                System.setProperty("com.rocketmq.remoting.clientAsyncSemaphoreValue", String.valueOf(deFiBusClientConfig.getPubWindowSize()));

                if (deFiBusClientConfig.getNamesrvAddr() != null) {
                    this.defaultMQProducer.setNamesrvAddr(deFiBusClientConfig.getNamesrvAddr());
                }
                if (!this.defaultMQProducer.getProducerGroup().equals(MixAll.CLIENT_INNER_PRODUCER_GROUP)) {
                    this.defaultMQProducer.changeInstanceNameToPID();
                }

                String instanceName = this.defaultMQProducer.getInstanceName() + DeFiBusConstant.INSTANCE_NAME_SEPERATER
                    + DeFiBusVersion.getVersionDesc(this.deFiBusClientConfig.getVersion());

                if (deFiBusClientConfig.getClusterPrefix() != null) {
                    instanceName = instanceName + DeFiBusConstant.INSTANCE_NAME_SEPERATER + deFiBusClientConfig.getClusterPrefix();
                }
                this.defaultMQProducer.setInstanceName(instanceName);

                this.defaultMQProducer.setPollNameServerInterval(deFiBusClientConfig.getPollNameServerInterval());
                this.defaultMQProducer.setRetryTimesWhenSendAsyncFailed(deFiBusClientConfig.getRetryTimesWhenSendAsyncFailed());
                this.defaultMQProducer.setRetryTimesWhenSendFailed(deFiBusClientConfig.getRetryTimesWhenSendFailed());
                this.defaultMQProducer.setHeartbeatBrokerInterval(deFiBusClientConfig.getHeartbeatBrokerInterval());
                this.defaultMQProducer.setPersistConsumerOffsetInterval(deFiBusClientConfig.getAckTime());
                deFiBusClientInstance
                    = DeFiBusClientManager.getInstance().getAndCreateDeFiBusClientInstance(defaultMQProducer, rpcHook);

                if (deFiBusClientConfig.getWsAddr() != null) {
                    DeFiBusClientAPIImpl deFiClientAPI = (DeFiBusClientAPIImpl) deFiBusClientInstance.getMQClientAPIImpl();
                    deFiClientAPI.setWsAddr(deFiBusClientConfig.getWsAddr());
                }

                deFiBusProducerImpl = new DeFiBusProducerImpl(this, deFiBusClientConfig, deFiBusClientInstance);
                this.defaultMQProducer.start();
                deFiBusProducerImpl.startUpdateClusterInfoTask();
            } catch (MQClientException e) {
                LOG.warn("DeFiBusProducer start client failed {}", e.getMessage());
                isStart.set(false);
                throw e;
            } catch (Exception e) {
                LOG.warn("DeFiBusProducer start client failed", e);
                isStart.set(false);
                throw new MQClientException("DeFiBusProducer start client failed", e);
            }

            MessageClientIDSetter.createUniqID();

            LOG.info("DeFiBusProducer start ok");
        } else {
            LOG.warn("DeFiBusProducer already started");
        }
    }

    public void shutdown() {
        if (isStart.compareAndSet(true, false)) {
            this.defaultMQProducer.shutdown();
            LOG.info("DeFiBusProducer [{}] shutdown", defaultMQProducer.getInstanceName());
        } else {
            LOG.info("DeFiBusProducer [{}] already shutdown", defaultMQProducer.getInstanceName());
        }
    }

    public DefaultMQProducer getDefaultMQProducer() {
        return defaultMQProducer;
    }

    //sync Request-Response interface
    public Message request(Message msg, long timeout)
        throws InterruptedException, RemotingException, MQClientException, MQBrokerException {
        return deFiBusProducerImpl.request(msg, timeout);
    }

    //async Request-Response interface
    public void request(Message msg, RRCallback rrCallback, long timeout)
        throws InterruptedException, RemotingException, MQClientException, MQBrokerException {
        deFiBusProducerImpl.request(msg, null, rrCallback, timeout);
    }

    public void request(Message msg, SendCallback sendCallback, RRCallback rrCallback, long timeout)
        throws InterruptedException, RemotingException, MQClientException, MQBrokerException {
        deFiBusProducerImpl.request(msg, sendCallback, rrCallback, timeout);
    }

    public void reply(Message replyMsg, SendCallback sendCallback)
        throws InterruptedException, RemotingException, MQClientException, MQBrokerException {
        deFiBusProducerImpl.reply(replyMsg, sendCallback);
    }

    //async publish with callback
    public void publish(Message msg, SendCallback sendCallback)
        throws MQClientException, RemotingException, InterruptedException {
        this.deFiBusProducerImpl.publish(msg, sendCallback);
    }

    //async public
    public void publish(Message msg) throws MQClientException, RemotingException, InterruptedException {
        this.deFiBusProducerImpl.publish(msg);
    }

    public void publish(
        Collection<Message> msgs) throws MQClientException, RemotingException, MQBrokerException, InterruptedException {
        this.deFiBusProducerImpl.publish(msgs);
    }

    public String getNamesrvAddr() {
        return defaultMQProducer.getNamesrvAddr();
    }

    public void setNamesrvAddr(String namesrvAddr) {
        defaultMQProducer.setNamesrvAddr(namesrvAddr);
    }

    public boolean isStart() {
        return isStart.get();
    }

    public DeFiBusClientConfig getDeFiBusClientConfig() {
        return deFiBusClientConfig;
    }

    public void updateSendNearbyMapping(Map<String, Boolean> newMapping) {
        this.deFiBusProducerImpl.updateSendNearbyMapping(newMapping);
    }
}
