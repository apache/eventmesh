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

package org.apache.eventmesh.runtime.core.protocol.amqp.service;

import org.apache.eventmesh.runtime.core.protocol.amqp.exception.AmqpException;
import org.apache.eventmesh.runtime.core.protocol.amqp.metadata.model.QueueInfo;

import java.util.Map;
import java.util.Set;

/**
 * provide services to use QueueContainer
 */
public interface QueueService {
    /**
     * Declare a queue.
     *
     * @param connectionId    connection id
     * @param virtualHostName namespace
     * @param queue           the name of the queue nowait are ignored; and sending nowait makes this method a no-op, so we
     *                        default it to false.
     * @param durable         true if we are declaring a durable queue (the exchange will survive a server restart)
     * @param exclusive       true if we are declaring an exclusive queue (restricted to this connection)
     * @param autoDelete      true if the server should delete the queue when it is no longer in use
     * @param arguments       other properties (construction arguments) for the queue
     */
    QueueInfo queueDeclare(String connectionId, String virtualHostName, String queue,
                           boolean durable, boolean exclusive, boolean autoDelete, Map<String, Object> arguments)
        throws AmqpException;


    /**
     * Delete a queue.
     *
     * @param virtualHostName namespace
     * @param defaultQueue    default queue in channel, null if used in console
     * @param queue           the name of the queue
     * @param ifUnused        true if the queue should be deleted only if not in use
     * @param ifEmpty         true if the queue should be deleted only if empty
     */
    void queueDelete(String virtualHostName, QueueInfo defaultQueue, String queue,
                     boolean ifUnused, boolean ifEmpty) throws AmqpException;

    /**
     * Bind a queue to an exchange.
     *
     * @param virtualHostName namespace
     * @param queue           the name of the queue
     * @param exchange        the name of the exchange
     * @param bindingKey      the key to use for the binding
     * @param argumentsTable  other properties (binding parameters)
     */
    void queueBind(String virtualHostName, QueueInfo defaultQueue,
                   String queue, String exchange, String bindingKey, Map<String, Object> argumentsTable)
        throws AmqpException;


    /**
     * Unbinds a queue from an exchange.
     *
     * @param virtualHostName namespace
     * @param queue           the name of the queue
     * @param exchange        the name of the exchange
     * @param bindingKey      the key to use for the binding
     * @param arguments       other properties (binding parameters)
     */
    void queueUnbind(String virtualHostName,
                     String queue, String exchange, String bindingKey, Map<String, Object> arguments) throws AmqpException;

    /**
     * Purges the contents of the given queue.
     *
     * @param virtualHostName namespace
     * @param queue           the name of the queue
     */
    void queuePurge(String virtualHostName, String queue);


    QueueInfo getQueue(String virtualHostName, String queue);

    boolean checkExist(String virtualHostName, String queue);

    Set<String> getBindings(String virtualHostName, String queue) throws Exception;

    Set<String> getQueueList(String virtualHostName) throws Exception;


}
