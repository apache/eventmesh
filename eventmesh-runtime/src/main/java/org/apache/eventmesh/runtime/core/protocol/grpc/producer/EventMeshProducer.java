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

package org.apache.eventmesh.runtime.core.protocol.grpc.producer;

import org.apache.eventmesh.api.RequestReplyCallback;
import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.runtime.common.ServiceState;
import org.apache.eventmesh.runtime.configuration.EventMeshGrpcConfiguration;
import org.apache.eventmesh.runtime.core.consumergroup.ProducerGroupConf;
import org.apache.eventmesh.runtime.core.plugin.MQProducerWrapper;
import org.apache.eventmesh.runtime.util.EventMeshUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

public class EventMeshProducer {

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    private ProducerGroupConf producerGroupConfig;

    private MQProducerWrapper mqProducerWrapper;

    private ServiceState serviceState;

    public void send(SendMessageContext sendMsgContext, SendCallback sendCallback)
        throws Exception {
        mqProducerWrapper.send(sendMsgContext.getEvent(), sendCallback);
    }

    public void request(SendMessageContext sendMsgContext, RequestReplyCallback rrCallback, long timeout)
        throws Exception {
        mqProducerWrapper.request(sendMsgContext.getEvent(), rrCallback, timeout);
    }

    public void reply(SendMessageContext sendMessageContext, SendCallback sendCallback) throws Exception {
        mqProducerWrapper.reply(sendMessageContext.getEvent(), sendCallback);
    }

    public MQProducerWrapper getMqProducerWrapper() {
        return mqProducerWrapper;
    }

    public synchronized void init(EventMeshGrpcConfiguration eventMeshGrpcConfiguration,
                                  ProducerGroupConf producerGroupConfig) throws Exception {
        this.producerGroupConfig = producerGroupConfig;

        Properties keyValue = new Properties();
        keyValue.put("producerGroup", producerGroupConfig.getGroupName());
        keyValue.put("instanceName", EventMeshUtil.buildMeshClientID(
            producerGroupConfig.getGroupName(), eventMeshGrpcConfiguration.eventMeshCluster));

        //TODO for defibus
        keyValue.put("eventMeshIDC", eventMeshGrpcConfiguration.eventMeshIDC);
        mqProducerWrapper = new MQProducerWrapper(
            eventMeshGrpcConfiguration.eventMeshConnectorPluginType);
        mqProducerWrapper.init(keyValue);
        serviceState = ServiceState.INITED;
        logger.info("EventMeshProducer [{}] inited...........", producerGroupConfig.getGroupName());
    }

    public synchronized void start() throws Exception {
        if (serviceState == null || ServiceState.RUNNING.equals(serviceState)) {
            return;
        }

        mqProducerWrapper.start();
        serviceState = ServiceState.RUNNING;
        logger.info("EventMeshProducer [{}] started..........", producerGroupConfig.getGroupName());
    }

    public synchronized void shutdown() throws Exception {
        if (serviceState == null || ServiceState.INITED.equals(serviceState)) {
            return;
        }

        mqProducerWrapper.shutdown();
        serviceState = ServiceState.STOPED;
        logger.info("EventMeshProducer [{}] shutdown.........", producerGroupConfig.getGroupName());
    }

    public ProducerGroupConf getProducerGroupConfig() {
        return this.producerGroupConfig;
    }

    public ServiceState getStatus() {
        return this.serviceState;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("eventMeshProducer={").append("status=").append(serviceState.name()).append(",").append("producerGroupConfig=")
            .append(producerGroupConfig).append("}");
        return sb.toString();
    }
}
