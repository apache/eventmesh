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

package com.webank.api.producer;

import com.webank.defibus.client.impl.producer.RRCallback;
import com.webank.eventmesh.common.config.CommonConfiguration;
import io.openmessaging.producer.Producer;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.remoting.exception.RemotingException;

public interface MeshMQProducer extends Producer {

    void init(CommonConfiguration commonConfiguration, String producerGroup) throws Exception;

    void start() throws Exception;

    void send(org.apache.rocketmq.common.message.Message message, org.apache.rocketmq.client.producer.SendCallback sendCallback) throws Exception;

    void request(org.apache.rocketmq.common.message.Message message, org.apache.rocketmq.client.producer.SendCallback sendCallback, RRCallback rrCallback, long timeout) throws InterruptedException, RemotingException, MQClientException, MQBrokerException;

    org.apache.rocketmq.common.message.Message request(org.apache.rocketmq.common.message.Message message, long timeout) throws Exception;

    boolean reply(final Message message, final SendCallback sendCallback) throws Exception;

    DefaultMQProducer getDefaultMQProducer();

}
