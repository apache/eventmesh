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

package org.apache.eventmesh.runtime.core.protocol.amqp.metadata;

import org.apache.eventmesh.runtime.boot.EventMeshAmqpServer;
import org.apache.eventmesh.runtime.core.protocol.amqp.metadata.manager.ExchangeManager;
import org.apache.eventmesh.runtime.core.protocol.amqp.metadata.manager.ExchangeManagerMemoryImpl;
import org.apache.eventmesh.runtime.core.protocol.amqp.metadata.manager.QueueManager;
import org.apache.eventmesh.runtime.core.protocol.amqp.metadata.manager.QueueManagerMemoryImpl;

public class MetaStore implements AutoCloseable {

    private EventMeshAmqpServer amqpServer;

    private ExchangeManager exchangeManager;

    private QueueManager queueManager;

    public MetaStore(EventMeshAmqpServer amqpServer) {
        this.amqpServer = amqpServer;
        this.exchangeManager = new ExchangeManagerMemoryImpl();
        this.queueManager = new QueueManagerMemoryImpl();
    }

    public QueueManager queue() {
        return queueManager;
    }

    public ExchangeManager exchange() {
        return exchangeManager;
    }


    @Override
    public void close() throws Exception {

    }
}
