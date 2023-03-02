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
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.eventmesh.connector.mongodb;

import org.apache.eventmesh.connector.mongodb.consumer.MongodbConsumer;
import org.apache.eventmesh.connector.mongodb.producer.MongodbProducer;

import java.util.Properties;

import org.junit.After;
import org.junit.Before;

import de.bwaldvogel.mongo.MongoServer;
import de.bwaldvogel.mongo.backend.memory.MemoryBackend;

public class MongodbServer {
    private MongoServer server1;
    private MongoServer server2;
    public final MongodbConsumer mongodbConsumer = new MongodbConsumer();
    public final MongodbProducer mongodbProducer = new MongodbProducer();

    @Before
    public void setup() throws Exception {
        server1 = new MongoServer(new MemoryBackend());
        server1.bind("127.0.0.1", 27018);
        server2 = new MongoServer(new MemoryBackend());
        server2.bind("127.0.0.1", 27019);

        mongodbConsumer.init(new Properties());
        mongodbConsumer.start();
        mongodbProducer.init(new Properties());
        mongodbProducer.start();
    }

    @After
    public void shutdown() {
        mongodbConsumer.shutdown();
        mongodbProducer.shutdown();

        server1.shutdown();
        server2.shutdown();
    }
}
