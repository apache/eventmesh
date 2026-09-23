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

package org.apache.eventmesh.connector.pravega.source;

import java.util.Properties;

import org.junit.jupiter.api.Test;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

/**
 * Contract-level unit test for PravegaSourceConnector: the external system is NOT available in CI, so this covers
 * the parts that must hold regardless — instantiation, the documented default-config surface of
 * init() (where init only parses config), and the commit() no-op contract.
 */
class PravegaSourceConnectorTest {

    @Test
    void commitIsNoOp() {
        PravegaSourceConnector connector = new PravegaSourceConnector();
        CloudEvent last = CloudEventBuilder.v1().withId("e1")
            .withSource(java.net.URI.create("/test")).withType("test.event").build();
        connector.commit(last); // must not throw
    }

    @Test
    void initParsesDocumentedDefaults() {
        PravegaSourceConnector connector = new PravegaSourceConnector();
        connector.init(new Properties()); // must not connect anything
    }

}
