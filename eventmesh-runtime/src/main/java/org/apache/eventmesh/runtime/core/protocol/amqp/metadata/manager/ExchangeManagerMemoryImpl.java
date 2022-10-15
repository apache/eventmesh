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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ExchangeManagerMemoryImpl implements ExchangeManager {

    private final Logger log = LoggerFactory.getLogger(this.getClass().getName());

    private ConcurrentHashMap<String, ConcurrentHashMap<String, ExchangeInfo>> exchangeMapping = new ConcurrentHashMap<>();

    @Override
    public void createExchange(String virtualHostName, String exchangeName, ExchangeInfo meta) throws AmqpNotFoundException {
        ConcurrentHashMap<String, ExchangeInfo> infoMap = exchangeMapping.computeIfAbsent(virtualHostName, m -> new ConcurrentHashMap<>());
        if (infoMap == null) {
            log.error("virtualHost not found {}", virtualHostName);
            throw new AmqpNotFoundException("vhost not found");
        }
        if (!infoMap.containsKey(exchangeName)) {
            infoMap.put(exchangeName, meta);
        }
    }


    @Override
    public ExchangeInfo getExchange(String virtualHostName, String exchangeName) {
        ConcurrentHashMap<String, ExchangeInfo> map = exchangeMapping.get(virtualHostName);
        if (map != null) {
            return map.get(exchangeName);
        }
        return null;
    }

    @Override
    public void deleteExchange(String virtualHostName, String exchangeName) {
        ConcurrentHashMap<String, ExchangeInfo> map = exchangeMapping.get(virtualHostName);
        if (map != null) {
            map.remove(exchangeName);
        }

    }

    @Override
    public void exchangeBind(String virtualHostName, String exchange, String queue, BindingInfo bindingInfo) throws AmqpNotFoundException {
        ExchangeInfo exchangeInfo = getExchange(virtualHostName, exchange);
        if (exchangeInfo == null) {
            throw new AmqpNotFoundException("exchange not found");
        }
        exchangeInfo.addBinding(queue, bindingInfo);
    }

    @Override
    public void exchangeUnBind(String virtualHostName, String exchange, String queue, String bindingKey) throws AmqpNotFoundException {
        ExchangeInfo exchangeInfo = getExchange(virtualHostName, exchange);
        if (exchangeInfo == null) {
            throw new AmqpNotFoundException("exchange not found");
        }
        exchangeInfo.removeBinding(queue, bindingKey);
    }

    @Override
    public void exchangeUnBind(String virtualHostName, String exchangeName, String queue) throws AmqpNotFoundException {
        ExchangeInfo exchangeInfo = getExchange(virtualHostName, exchangeName);
        if (exchangeInfo == null) {
            throw new AmqpNotFoundException("exchange not found");
        }
        exchangeInfo.removeBinding(queue);
    }

    @Override
    public void exchangeUnBindAll(String virtualHostName, String exchangeName) throws AmqpNotFoundException {
        ExchangeInfo exchangeInfo = getExchange(virtualHostName, exchangeName);
        if (exchangeInfo == null) {
            throw new AmqpNotFoundException("exchange not found");
        }
        exchangeInfo.removeAll();
    }

    @Override
    public Set<BindingInfo> getBindings(String virtualHostName, String exchangeName) throws Exception {
        ExchangeInfo exchangeInfo = getExchange(virtualHostName, exchangeName);
        if (exchangeInfo == null) {
            throw new AmqpNotFoundException("exchange not found");
        }
        return exchangeInfo.getBindings();
    }

    @Override
    public boolean isQueueBindingExist(String virtualHostName, String exchangeName, String queue) throws AmqpNotFoundException {
        ExchangeInfo exchangeInfo = getExchange(virtualHostName, exchangeName);
        if (exchangeInfo == null) {
            throw new AmqpNotFoundException("exchange not found");
        }
        return exchangeInfo.isQueueBindingExist(queue);
    }

    @Override
    public boolean checkExist(String virtualHostName, String exchangeName) {
        ExchangeInfo exchangeInfo = getExchange(virtualHostName, exchangeName);
        return exchangeInfo != null;
    }

    @Override
    public Set<String> getExchangeList(String virtualHostName) throws Exception {
        ConcurrentHashMap<String, ExchangeInfo> map = exchangeMapping.get(virtualHostName);
        if (map != null) {
            return map.keySet();
        }
        return new HashSet<>();
    }
}
