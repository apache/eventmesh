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

package org.apache.eventmesh.runtime.core.protocol.producer;

import org.apache.eventmesh.api.RequestReplyCallback;
import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.common.config.CommonConfiguration;
import org.apache.eventmesh.runtime.common.ServiceState;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.consumergroup.ProducerGroupConf;
import org.apache.eventmesh.runtime.core.plugin.MQProducerWrapper;
import org.apache.eventmesh.runtime.util.EventMeshUtil;

import java.util.Properties;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EventMeshProducer {

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

    public synchronized void init(CommonConfiguration configuration,
        ProducerGroupConf producerGroupConfig) throws Exception {
        if (ServiceState.INITED == serviceState) {
            return;
        }
        this.producerGroupConfig = producerGroupConfig;

        Properties keyValue = new Properties();
        keyValue.put(EventMeshConstants.PRODUCER_GROUP, producerGroupConfig.getGroupName());
        keyValue.put(EventMeshConstants.INSTANCE_NAME, EventMeshUtil.buildMeshClientID(
            producerGroupConfig.getGroupName(), configuration.getEventMeshCluster()));

        keyValue.put(EventMeshConstants.EVENT_MESH_IDC, configuration.getEventMeshIDC());
        mqProducerWrapper = new MQProducerWrapper(
            configuration.getEventMeshStoragePluginType());
        mqProducerWrapper.init(keyValue);
        serviceState = ServiceState.INITED;
        log.info("EventMeshProducer [{}] inited...........", producerGroupConfig.getGroupName());
    }

    public synchronized void start() throws Exception {
        if (serviceState == null || ServiceState.RUNNING == serviceState) {
            return;
        }

        mqProducerWrapper.start();
        serviceState = ServiceState.RUNNING;
        log.info("EventMeshProducer [{}] started..........", producerGroupConfig.getGroupName());
    }

    public synchronized void shutdown() throws Exception {
        if (serviceState == null || ServiceState.STOPPED == serviceState) {
            return;
        }

        mqProducerWrapper.shutdown();
        serviceState = ServiceState.STOPPED;
        log.info("EventMeshProducer [{}] shutdown.........", producerGroupConfig.getGroupName());
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

    public MQProducerWrapper getMqProducerWrapper() {
        return mqProducerWrapper;
    }

    public boolean isStarted() {
        return serviceState == ServiceState.RUNNING;
    }
}
