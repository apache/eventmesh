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

package org.apache.eventmesh.runtime.core.protocol.amqp.metadata.model;

import org.apache.eventmesh.runtime.core.protocol.amqp.exchange.ExchangeType;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;


public class ExchangeInfo {

    private String exchangeName;
    private boolean durable;
    private boolean autoDelete;
    private ExchangeType exchangeType;

    private Map<String, Set<BindingInfo>> bindings;


    public ExchangeInfo() {
    }

    public ExchangeInfo(String exchangeName, boolean durable, boolean autoDelete, ExchangeType exchangeType) {
        this.exchangeName = exchangeName;
        this.durable = durable;
        this.autoDelete = autoDelete;
        this.exchangeType = exchangeType;
    }

    public void setExchangeName(String exchangeName) {
        this.exchangeName = exchangeName;
    }

    public void setDurable(boolean durable) {
        this.durable = durable;
    }

    public void setAutoDelete(boolean autoDelete) {
        this.autoDelete = autoDelete;
    }

    public void setExchangeType(ExchangeType exchangeType) {
        this.exchangeType = exchangeType;
    }

    public String getExchangeName() {
        return exchangeName;
    }

    public boolean isDurable() {
        return durable;
    }

    public boolean isAutoDelete() {
        return autoDelete;
    }

    public ExchangeType getExchangeType() {
        return exchangeType;
    }

    public synchronized void addBinding(String queue, BindingInfo bindingInfo) {
        Set<BindingInfo> infoSet = bindings.computeIfAbsent(queue, s -> new HashSet<>());
        infoSet.add(bindingInfo);
    }

    public synchronized void removeBinding(String queue, String bindingKey) {
        Set<BindingInfo> infoSet = bindings.computeIfAbsent(queue, s -> new HashSet<>());
        Iterator<BindingInfo> iterator = infoSet.iterator();
        while (iterator.hasNext()) {
            BindingInfo bind = iterator.next();
            if (bind.getBindingKey().equals(bindingKey)) {
                iterator.remove();
            }
        }
    }

    public synchronized void removeBinding(String queue) {
        bindings.remove(queue);
    }

    public synchronized void removeAll() {
        bindings.clear();
    }

    public Set<BindingInfo> getBindings() {
        Set<BindingInfo> infoSet = new HashSet<>();
        bindings.values().forEach(s -> {
            infoSet.addAll(s);
        });
        return infoSet;
    }

    public boolean isQueueBindingExist(String queue) {
        return bindings.containsKey(queue);
    }


}
