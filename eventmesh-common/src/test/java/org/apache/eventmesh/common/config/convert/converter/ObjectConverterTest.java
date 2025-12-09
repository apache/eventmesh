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

package org.apache.eventmesh.common.config.convert.converter;

import org.apache.eventmesh.common.config.ConfigField;
import org.apache.eventmesh.common.config.ConfigInfo;
import org.apache.eventmesh.common.config.convert.ConvertInfo;

import java.lang.reflect.Field;
import java.util.Properties;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import lombok.Data;

public class ObjectConverterTest {

    @Test
    public void testConvert() {
        ObjectConverter converter = new ObjectConverter();
        ConvertInfo convertInfo = new ConvertInfo();
        ConfigInfo configInfo = new ConfigInfo();
        Properties properties = new Properties();
        properties.put("name", "test");
        properties.put("age", "18");
        convertInfo.setProperties(properties);
        convertInfo.setConfigInfo(configInfo);
        convertInfo.setClazz(User.class);
        
        Object converted = converter.convert(convertInfo);
        Assertions.assertTrue(converted instanceof User);
        User user = (User) converted;
        Assertions.assertEquals("test", user.getName());
        Assertions.assertEquals(18, user.getAge());
    }

    @Test
    public void testConvertWithField() throws NoSuchFieldException {
        ObjectConverter converter = new ObjectConverter();
        ConvertInfo convertInfo = new ConvertInfo();
        ConfigInfo configInfo = new ConfigInfo();
        Properties properties = new Properties();
        properties.put("name", "test");
        properties.put("age", "18");
        convertInfo.setProperties(properties);
        convertInfo.setConfigInfo(configInfo);
        
        Field field = Config.class.getDeclaredField("user");
        convertInfo.setField(field);
        convertInfo.setClazz(User.class);

        Object converted = converter.convert(convertInfo);
        Assertions.assertTrue(converted instanceof User);
        User user = (User) converted;
        Assertions.assertEquals("test", user.getName());
        Assertions.assertEquals(18, user.getAge());
    }

    @Data
    public static class User {
        @ConfigField(field = "name")
        private String name;
        @ConfigField(field = "age")
        private int age;
    }

    @Data
    public static class Config {
        @ConfigField(field = "")
        private User user;
    }
}
