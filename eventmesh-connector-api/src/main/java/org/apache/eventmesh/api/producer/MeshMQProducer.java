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

package org.apache.eventmesh.api.producer;

import java.util.Properties;

import io.openmessaging.api.Message;
import io.openmessaging.api.Producer;
import io.openmessaging.api.SendCallback;

import org.apache.eventmesh.api.RRCallback;

public interface MeshMQProducer extends Producer {

    void init(Properties properties) throws Exception;

    @Override
    void start();

    void send(Message message, SendCallback sendCallback) throws Exception;

    void request(Message message, SendCallback sendCallback, RRCallback rrCallback, long timeout) throws Exception;

    Message request(Message message, long timeout) throws Exception;

    boolean reply(final Message message, final SendCallback sendCallback) throws Exception;

    MeshMQProducer getMeshMQProducer();

    String buildMQClientId();

    void setExtFields();

    void getDefaultTopicRouteInfoFromNameServer(String topic, long timeout) throws Exception;

}
