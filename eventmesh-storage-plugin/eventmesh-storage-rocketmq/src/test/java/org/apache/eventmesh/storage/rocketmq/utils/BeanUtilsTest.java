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

package org.apache.eventmesh.storage.rocketmq.utils;

import org.apache.eventmesh.storage.rocketmq.config.ClientConfig;
import org.apache.eventmesh.storage.rocketmq.domain.NonStandardKeys;

import java.util.Properties;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class BeanUtilsTest {

    private final Properties properties = new Properties();

    public static class CustomizedConfig extends ClientConfig {

        static final String STRING_TEST = "string.test";
        String stringTest = "foobar";

        static final String DOUBLE_TEST = "double.test";
        double doubleTest = 123.0;

        static final String LONG_TEST = "long.test";
        long longTest = 123L;

        String getStringTest() {
            return stringTest;
        }

        public void setStringTest(String stringTest) {
            this.stringTest = stringTest;
        }

        double getDoubleTest() {
            return doubleTest;
        }

        public void setDoubleTest(final double doubleTest) {
            this.doubleTest = doubleTest;
        }

        long getLongTest() {
            return longTest;
        }

        public void setLongTest(final long longTest) {
            this.longTest = longTest;
        }

        public CustomizedConfig() {
        }
    }

    @BeforeEach
    public void before() {
        properties.put(NonStandardKeys.MAX_REDELIVERY_TIMES, 120);
        properties.put(CustomizedConfig.STRING_TEST, "kaka");
        properties.put(NonStandardKeys.CONSUMER_GROUP, "Default_Consumer_Group");
        properties.put(NonStandardKeys.MESSAGE_CONSUME_TIMEOUT, 101);

        properties.put(CustomizedConfig.LONG_TEST, 1234567890L);
        properties.put(CustomizedConfig.DOUBLE_TEST, 10.234);
    }

    @Test
    public void testPopulate() {
        CustomizedConfig config = BeanUtils.populate(properties, CustomizedConfig.class);

        Assertions.assertEquals(120, config.getRmqMaxRedeliveryTimes());
        Assertions.assertEquals("kaka", config.getStringTest());
        Assertions.assertEquals("Default_Consumer_Group", config.getRmqConsumerGroup());
        Assertions.assertEquals(101, config.getRmqMessageConsumeTimeout());
        Assertions.assertEquals(1234567890L, config.getLongTest());
        Assertions.assertEquals(10.234, config.getDoubleTest(), 0.000001);
    }

    @Test
    public void testPopulate_ExistObj() {
        CustomizedConfig config = new CustomizedConfig();
        config.setConsumerId("NewConsumerId");

        Assertions.assertEquals("NewConsumerId", config.getConsumerId());

        BeanUtils.populate(properties, config);

        Assertions.assertEquals(120, config.getRmqMaxRedeliveryTimes());
        Assertions.assertEquals("kaka", config.getStringTest());
        Assertions.assertEquals("Default_Consumer_Group", config.getRmqConsumerGroup());
        Assertions.assertEquals(101, config.getRmqMessageConsumeTimeout());
        Assertions.assertEquals(1234567890L, config.getLongTest());
        Assertions.assertEquals(10.234, config.getDoubleTest(), 0.000001);
    }

}
