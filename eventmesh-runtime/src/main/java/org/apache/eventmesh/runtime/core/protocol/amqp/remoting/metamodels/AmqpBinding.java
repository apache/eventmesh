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

package org.apache.eventmesh.runtime.core.protocol.amqp.remoting.metamodels;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * a class of bindings between AmqpQueue and AmqpExchange
 * store relationship (binding keys) between queue and exchange
 */
public abstract class AmqpBinding {

    /**
     * Message router type.
     * Direct message router is used to bind {@link AmqpExchange.Type#Direct} exchange.
     * Fanout message router is used to bind {@link AmqpExchange.Type#Fanout} exchange.
     * Topic message router is used to bind {@link AmqpExchange.Type#Topic} exchange.
     * Headers message router is used to bind {@link AmqpExchange.Type#Headers} exchange.
     */
    enum Type {
        Direct,
        Fanout,
        Topic,
        Headers;
    }

    @Getter
    @Setter
    protected AmqpQueue amqpQueue;

    @Getter
    @Setter
    protected AmqpExchange amqpExchange;
    protected final AmqpBinding.Type bindingType;

    @Getter
    @Setter
    protected Set<String> bindingKeys;

    @Getter
    @Setter
    protected Map<String, Object> arguments;

    protected AmqpBinding(Type bindingType) {
        this.bindingType = bindingType;
        this.bindingKeys = new HashSet<>();
    }

    public Type getType() {
        return bindingType;
    }

    public void addBindingKey(String bindingKey) {
        this.bindingKeys.add(bindingKey);
    }

    public abstract boolean isMatch(Map<String, Object> properties);

}