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

package org.apache.eventmesh.runtime.core.producer;

import org.apache.eventmesh.api.RequestReplyCallback;
import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.config.CommonConfiguration;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.plugin.MQProducerWrapper;
import org.apache.eventmesh.runtime.core.protocol.RetryContext;
import org.apache.eventmesh.runtime.util.EventMeshUtil;

import org.apache.commons.lang3.StringUtils;

import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EventMeshProducer {

    protected AtomicBoolean started = new AtomicBoolean(Boolean.FALSE);
    protected AtomicBoolean inited = new AtomicBoolean(Boolean.FALSE);

    private final ProducerGroupConf producerGroupConfig;
    private final CommonConfiguration commonConfiguration;

    private MQProducerWrapper mqProducerWrapper;

    public EventMeshProducer(ProducerGroupConf producerGroupConfig, CommonConfiguration commonConfiguration) {
        this.producerGroupConfig = producerGroupConfig;
        this.commonConfiguration = commonConfiguration;
    }

    public void send(RetryContext context, SendCallback sendCallback) throws Exception {
        mqProducerWrapper.send(context.getEvent(), sendCallback);
    }

    public void request(RetryContext context, RequestReplyCallback rrCallback, long timeout)
        throws Exception {
        mqProducerWrapper.request(context.getEvent(), rrCallback, timeout);
    }

    public boolean reply(RetryContext context, SendCallback sendCallback) throws Exception {
        mqProducerWrapper.reply(context.getEvent(), sendCallback);
        return true;
    }

    public void init() throws Exception {
        if (!inited.compareAndSet(false, true)) {
            return;
        }

        Properties keyValue = new Properties();
        keyValue.put(EventMeshConstants.PRODUCER_GROUP, producerGroupConfig.getGroupName());
        keyValue.put(EventMeshConstants.INSTANCE_NAME, EventMeshUtil.buildMeshClientID(producerGroupConfig.getGroupName(),
                commonConfiguration.getEventMeshCluster()));
        if (StringUtils.isNotBlank(producerGroupConfig.getToken())) {
            keyValue.put(Constants.PRODUCER_TOKEN, producerGroupConfig.getToken());
        }

        //TODO for defibus
        keyValue.put(EventMeshConstants.EVENT_MESH_IDC, commonConfiguration.getEventMeshIDC());
        mqProducerWrapper = new MQProducerWrapper(commonConfiguration.getEventMeshStoragePluginType());
        mqProducerWrapper.init(keyValue);
        log.info("EventMeshProducer [{}] inited.............", producerGroupConfig.getGroupName());
    }


    public void initTcp(String sysId, String group) throws Exception {
        if (!inited.compareAndSet(false, true)) {
            return;
        }

        Properties keyValue = new Properties();
        keyValue.put(EventMeshConstants.PRODUCER_GROUP, group);
        keyValue.put(EventMeshConstants.INSTANCE_NAME, EventMeshUtil
                .buildMeshTcpClientID(sysId, EventMeshConstants.PURPOSE_PUB_UPPER_CASE,
                        commonConfiguration.getEventMeshCluster()));
        if (StringUtils.isNotBlank(producerGroupConfig.getToken())) {
            keyValue.put(Constants.PRODUCER_TOKEN, producerGroupConfig.getToken());
        }

        //TODO for defibus
        keyValue.put(EventMeshConstants.EVENT_MESH_IDC, commonConfiguration.getEventMeshIDC());
        mqProducerWrapper = new MQProducerWrapper(commonConfiguration.getEventMeshStoragePluginType());
        mqProducerWrapper.init(keyValue);
        log.info("EventMeshProducer [{}] inited.............", producerGroupConfig.getGroupName());
    }


    public void start() throws Exception {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        mqProducerWrapper.start();
        log.info("EventMeshProducer [{}] started.............", producerGroupConfig.getGroupName());
    }

    public void shutdown() throws Exception {
        if (!inited.compareAndSet(true, false)) {
            return;
        }
        if (!started.compareAndSet(true, false)) {
            return;
        }
        mqProducerWrapper.shutdown();
        log.info("EventMeshProducer [{}] shutdown.............", producerGroupConfig.getGroupName());
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("eventMeshProducer={")
            .append("inited=").append(inited.get()).append(",")
            .append("started=").append(started.get()).append(",")
            .append("producerGroupConfig=").append(producerGroupConfig).append("}");
        return sb.toString();
    }

    public ProducerGroupConf getProducerGroupConfig() {
        return producerGroupConfig;
    }

    public MQProducerWrapper getMqProducerWrapper() {
        return mqProducerWrapper;
    }

    public AtomicBoolean getInited() {
        return inited;
    }

    public AtomicBoolean getStarted() {
        return started;
    }

    public boolean isStarted() {
        return started.get();
    }
}
