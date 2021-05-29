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

package org.apache.eventmesh.connector.kafka.consumer;

import org.apache.eventmesh.connector.kafka.common.Constants;
import org.apache.eventmesh.connector.kafka.config.ConfigurationWrapper;
import org.apache.eventmesh.connector.kafka.producer.KafkaMQProducerImplExample;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class KafkaConsumerConfigTest {

    private KafkaConsumerConfig kafkaConsumerConfig;

    @Before
    public void testInit() {
        String filePath = KafkaMQProducerImplExample.class.getClassLoader().getResource(Constants.KAFKA_CONF_FILE).getPath();
        ConfigurationWrapper configurationWrapper = new ConfigurationWrapper(filePath, false);
        kafkaConsumerConfig = new KafkaConsumerConfig(configurationWrapper);
    }

    @Test
    public void testGetBoosStrapServer() {
        Assert.assertEquals("localhost:9092", kafkaConsumerConfig.getBoosStrapServer());
    }

    @Test
    public void testSetBoosStrapServer() {
        kafkaConsumerConfig.setBoosStrapServer("localhost:9092");
    }

    @Test
    public void testGetGroupId() {
        String groupId = kafkaConsumerConfig.getGroupId();
        Assert.assertNull(groupId);
    }

    @Test
    public void testSetGroupId() {
    }

    @Test
    public void getKeyDeserializer() {
        Assert.assertEquals("org.apache.kafka.common.serialization.StringDeserializer",
                kafkaConsumerConfig.getKeyDeserializer());
    }

    @Test
    public void setKeyDeserializer() {
    }

    @Test
    public void getValueDeserializer() {
        Assert.assertEquals("org.apache.eventmesh.connector.kafka.common.OpenMessageDeserializer",
                kafkaConsumerConfig.getValueDeserializer());
    }

    @Test
    public void setValueDeserializer() {
    }

    @Test
    public void testGetFetchMinBytes() {
    }

    @Test
    public void testSetFetchMinBytes() {
    }

    @Test
    public void testGetFetchMaxWaitMs() {
    }

    @Test
    public void testSetFetchMaxWaitMs() {
    }

    @Test
    public void testGetMaxPartitionFetchBytes() {
    }

    @Test
    public void testSetMaxPartitionFetchBytes() {
    }

    @Test
    public void testGetSessionTimeoutMs() {
    }

    @Test
    public void testSetSessionTimeoutMs() {
    }

    @Test
    public void testGetAutoOffsetReset() {
    }

    @Test
    public void testSetAutoOffsetReset() {
    }

    @Test
    public void testGetEnableAutoCommit() {
        Assert.assertFalse(kafkaConsumerConfig.getEnableAutoCommit());
    }

    @Test
    public void testSetEnableAutoCommit() {
    }

    @Test
    public void testGetMaxPollRecords() {
    }

    @Test
    public void testSetMaxPollRecords() {
    }
}