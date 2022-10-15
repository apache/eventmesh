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
import org.apache.eventmesh.runtime.core.protocol.amqp.metadata.model.BindingInfo;
import org.apache.eventmesh.runtime.core.protocol.amqp.metadata.model.ExchangeInfo;

import java.util.Map;
import java.util.Set;

/**
 * provide services to use ExchangeContainer
 */
public interface ExchangeService {

    /**
     * Declare a exchange.
     *
     * @param virtualHostName namespace
     * @param exchange        the name of the exchange
     * @param type            the exchange type
     *                        are ignored; and sending nowait makes this method a no-op, so we default it to false.
     * @param durable         true if we are declaring a durable exchange (the exchange will survive a server restart)
     * @param autoDelete      true if the server should delete the exchange when it is no longer in use
     * @param internal        true if the exchange is internal, i.e. can't be directly published to by a client
     * @param arguments       other properties (construction arguments) for the exchange
     * @return completableFuture of process result
     */
    void exchangeDeclare(String virtualHostName, String exchange, String type, boolean durable, boolean autoDelete, boolean internal,
                         Map<String, Object> arguments) throws AmqpException;

    /**
     * Delete a exchange.
     *
     * @param virtualHostName namespace
     * @param exchange        the name of the exchange
     * @param ifUnused        true to indicate that the exchange is only to be deleted if it is unused
     * @return completableFuture of process result
     */
    void exchangeDelete(String virtualHostName, String exchange, boolean ifUnused) throws AmqpException;


    Set<BindingInfo> getBindings(String virtualHostName, String exchange) throws Exception;

    ExchangeInfo getExchange(String virtualHostName, String exchange);

    boolean checkExchangeExist(String virtualHostName, String exchange);

    Set<String> getExchangeList(String virtualHostName) throws Exception;

}