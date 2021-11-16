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

package org.apache.eventmesh.connector.rocketmq.consumer;

import org.apache.eventmesh.api.AbstractContext;
import org.apache.eventmesh.api.EventListener;
import org.apache.eventmesh.api.consumer.Consumer;
import org.apache.eventmesh.api.consumer.MeshMQPushConsumer;
import org.apache.eventmesh.connector.rocketmq.MessagingAccessPointImpl;
import org.apache.eventmesh.connector.rocketmq.common.Constants;
import org.apache.eventmesh.connector.rocketmq.common.EventMeshConstants;
import org.apache.eventmesh.connector.rocketmq.config.ClientConfiguration;
import org.apache.eventmesh.connector.rocketmq.config.ConfigurationWrapper;
import org.apache.eventmesh.connector.rocketmq.patch.EventMeshConsumeConcurrentlyContext;
import org.apache.eventmesh.connector.rocketmq.utils.OMSUtil;

import org.apache.rocketmq.client.impl.consumer.ConsumeMessageConcurrentlyService;
import org.apache.rocketmq.client.impl.consumer.ConsumeMessageService;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.common.protocol.heartbeat.MessageModel;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.cloudevents.CloudEvent;
import io.openmessaging.api.AsyncGenericMessageListener;
import io.openmessaging.api.AsyncMessageListener;
import io.openmessaging.api.GenericMessageListener;
import io.openmessaging.api.Message;
import io.openmessaging.api.MessageListener;
import io.openmessaging.api.MessageSelector;
import io.openmessaging.api.MessagingAccessPoint;

public class RocketMQConsumerImpl implements Consumer {

    public Logger logger = LoggerFactory.getLogger(this.getClass());

    public Logger messageLogger = LoggerFactory.getLogger("message");

    private PushConsumerImpl pushConsumer;

    @Override
    public synchronized void init(Properties keyValue) throws Exception {
        ConfigurationWrapper configurationWrapper =
            new ConfigurationWrapper(getRocketMqConfigFile(), false);
        final ClientConfiguration clientConfiguration =
            new ClientConfiguration(configurationWrapper);
        clientConfiguration.init();
        boolean isBroadcast = Boolean.parseBoolean(keyValue.getProperty("isBroadcast"));
        String consumerGroup = keyValue.getProperty("consumerGroup");
        String instanceName = keyValue.getProperty("instanceName");


        if (isBroadcast) {
            consumerGroup = Constants.BROADCAST_PREFIX + consumerGroup;
        }

        String namesrvAddr = clientConfiguration.namesrvAddr;
        Properties properties = new Properties();
        properties.put("ACCESS_POINTS", namesrvAddr);
        properties.put("REGION", "namespace");
        properties.put("instanceName", instanceName);
        properties.put("CONSUMER_ID", consumerGroup);
        if (isBroadcast) {
            properties.put("MESSAGE_MODEL", MessageModel.BROADCASTING.name());
        } else {
            properties.put("MESSAGE_MODEL", MessageModel.CLUSTERING.name());
        }

        pushConsumer = new PushConsumerImpl(properties);
    }

    @Override
    public void subscribe(String topic, EventListener listener) throws Exception {
        pushConsumer.subscribe(topic, "*", listener);
    }

    @Override
    public boolean isStarted() {
        return pushConsumer.isStarted();
    }

    @Override
    public boolean isClosed() {
        return pushConsumer.isClosed();
    }

    @Override
    public synchronized void start() {
        pushConsumer.start();
    }

    @Override
    public void updateOffset(List<CloudEvent> cloudEvents, AbstractContext context) {
        pushConsumer.updateOffset(cloudEvents, context);
    }

    @Override
    public void unsubscribe(String topic) {
        pushConsumer.unsubscribe(topic);
    }

    @Override
    public synchronized void shutdown() {
        pushConsumer.shutdown();
    }

    public Properties attributes() {
        return pushConsumer.attributes();
    }

    private String getRocketMqConfigFile() {
        // get from classpath
        String configFile = RocketMQConsumerImpl.class.getClassLoader()
            .getResource(EventMeshConstants.EVENTMESH_CONF_FILE).getPath();
        if (new File(configFile).exists()) {
            return configFile;
        }
        // get from config home
        configFile = EventMeshConstants.EVENTMESH_CONF_HOME + File.separator
            + EventMeshConstants.EVENTMESH_CONF_FILE;
        return configFile;
    }
}
