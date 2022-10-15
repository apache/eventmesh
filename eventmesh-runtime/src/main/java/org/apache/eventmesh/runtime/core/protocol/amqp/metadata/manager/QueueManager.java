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

package org.apache.eventmesh.runtime.core.protocol.amqp.metadata.manager;

import org.apache.eventmesh.runtime.core.protocol.amqp.exception.AmqpNotFoundException;
import org.apache.eventmesh.runtime.core.protocol.amqp.metadata.model.QueueInfo;

import java.util.Set;

public interface QueueManager {

    void createQueue(String virtualHostName, final String queueName, QueueInfo meta) throws AmqpNotFoundException;


    QueueInfo getQueue(String virtualHostName, final String queueName);

    void deleteQueue(String virtualHostName, final String queueName);


    void queueBind(String virtualHostName, String queue, String exchangeName) throws AmqpNotFoundException;


    void queueUnBind(String virtualHostName, final String queue,
                     String exchangeName) throws AmqpNotFoundException;


    void queueUnBindAll(String virtualHostName, final String queue) throws AmqpNotFoundException;

    boolean checkExist(String virtualHostName, final String queue);

    Set<String> getQueueList(String virtualHostName);

    Set<String> getBindings(String virtualHostName, String queue);

}
