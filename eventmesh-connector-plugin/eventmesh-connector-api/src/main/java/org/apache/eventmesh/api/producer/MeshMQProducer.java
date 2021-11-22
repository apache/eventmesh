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
import org.apache.eventmesh.spi.EventMeshExtensionType;
import org.apache.eventmesh.spi.EventMeshSPI;

@EventMeshSPI(isSingleton = false, eventMeshExtensionType = EventMeshExtensionType.CONNECTOR)
public interface MeshMQProducer extends Producer {

    void init(Properties properties) throws Exception;

    void send(Message message, SendCallback sendCallback) throws Exception;

    void request(Message message, RRCallback rrCallback, long timeout) throws Exception;

    boolean reply(final Message message, final SendCallback sendCallback) throws Exception;

    void checkTopicExist(String topic) throws Exception;

    void setExtFields();

}
