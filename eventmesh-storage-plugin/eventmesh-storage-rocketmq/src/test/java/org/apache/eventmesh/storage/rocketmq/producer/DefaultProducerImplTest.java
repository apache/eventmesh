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

package org.apache.eventmesh.storage.rocketmq.producer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

public class DefaultProducerImplTest {

    @BeforeEach
    public void before() {
    }

    @AfterEach
    public void after() {
        // TBD:Remove topic
    }

    /**
     * @Test
     * public void testCreate_EmptyTopic() {
     *     MeshMQProducer meshPub = new RocketMQProducerImpl();
     *     try {
     *         meshPub.createTopic(" ");
     *         catch (OMSRuntimeException e) {
     *         assertThat(e.getMessage()).isEqualToIgnoringWhitespace("RocketMQ can not create topic");
     *     }
     * }
     *
     * @Test
     * public void testCreate_NullTopic() {
     *     MeshMQProducer meshPub = new RocketMQProducerImpl();
     *     try {
     *         meshPub.createTopic(null);
     *     } catch (OMSRuntimeException e) {
     *         String errorMessage = e.getMessage();
     *         assertThat(errorMessage).isEqualTo("RocketMQ can not create topic null");
     *     }
     * }
     */
}