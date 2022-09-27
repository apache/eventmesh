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

import lombok.Getter;

import java.util.HashSet;
import java.util.Set;

/**
 * Define Exchange in Amqp
 */
public class AmqpExchange {
    public enum Type {
        // Default exchange is a special exchange based on Direct exchange,
        // so it is not necessary to define Default exchange, instead, use Direct
        Direct,
        Fanout,
        Topic,
        Headers;

        public static Type value(String type) {
            if (type == null || type.length() == 0) {
                return null;
            }
            type = type.toLowerCase();
            switch (type) {
                case "direct":
                    return Direct;
                case "fanout":
                    return Fanout;
                case "topic":
                    return Topic;
                case "headers":
                    return Headers;
                default:
                    return null;
            }
        }
    }

    @Getter
    protected final String exchangeName;

    @Getter
    protected final AmqpExchange.Type exchangeType;

    /**
     * If set, the server will reply with Declare-Ok if the exchange already exists with the same name,
     * and raise an error if not.
     */
    protected boolean passive;

    /**
     * Durable exchanges remain active when a server restarts.
     * Non-durable exchanges (transient exchanges) are purged if/when a server restarts.
     */
    @Getter
    protected boolean durable;

    /**
     * If set, the exchange is deleted when all queues have finished using it.
     */
    @Getter
    protected boolean autoDelete;

    /**
     * If set, the exchange may not be used directly by publishers,
     * but only when bound to other exchanges.
     * Internal exchanges are used to construct wiring that is not visible to applications.
     */
    protected boolean internal;

    protected Set<AmqpQueue> queues;

    public AmqpExchange(String exchangeName, Type exchangeType, boolean passive, boolean durable, boolean autoDelete, boolean internal) {
        this.exchangeName = exchangeName;
        this.exchangeType = exchangeType;
        this.passive = passive;
        this.durable = durable;
        this.autoDelete = autoDelete;
        this.internal = internal;
        this.queues = new HashSet<>();
    }

    public int getQueueSize() {
        return queues.size();
    }

    public void addQueue(AmqpQueue queue) {
        queues.add(queue);
    }

    public void removeQueue(AmqpQueue queue) {
        queues.remove(queue);
    }
}