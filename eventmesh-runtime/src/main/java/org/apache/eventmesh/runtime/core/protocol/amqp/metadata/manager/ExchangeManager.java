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
import org.apache.eventmesh.runtime.core.protocol.amqp.metadata.model.BindingInfo;
import org.apache.eventmesh.runtime.core.protocol.amqp.metadata.model.ExchangeInfo;

import java.util.Set;

public interface ExchangeManager {

    void createExchange(String virtualHostName, final String exchangeName, ExchangeInfo meta) throws AmqpNotFoundException;

    ExchangeInfo getExchange(String virtualHostName, final String exchangeName);


    void deleteExchange(String virtualHostName, final String exchangeName);


    void exchangeBind(final String virtualHostName, String exchange, String queue,
                      BindingInfo bindingInfo) throws AmqpNotFoundException;

    void exchangeUnBind(final String virtualHostName, String exchange,
                        String queue, String bindingKey) throws AmqpNotFoundException;


    void exchangeUnBind(String virtualHostName, final String exchangeName, String queue) throws AmqpNotFoundException;

    void exchangeUnBindAll(String virtualHostName, final String exchangeName) throws AmqpNotFoundException;

    Set<BindingInfo> getBindings(String virtualHostName, String exchangeName) throws Exception;

    boolean isQueueBindingExist(String virtualHostName, String exchangeName, String queue) throws AmqpNotFoundException;

    boolean checkExist(String virtualHostName, String exchangeName);

    Set<String> getExchangeList(String virtualHostName) throws Exception;

}
