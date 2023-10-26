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

package org.apache.eventmesh.protocol.api;

import org.apache.eventmesh.common.protocol.ProtocolTransportObject;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ProtocolPluginFactoryTest {

    private static final String PROTOCOL_TYPE_NAME = "testProtocolType";

    @Test
    public void testGetProtocolAdaptorWhenMapEmpty() throws IllegalAccessException, NoSuchFieldException {
        Map<String, ProtocolAdaptor<ProtocolTransportObject>> mockProtocolAdaptorMap =
            new ConcurrentHashMap<>(16);
        ProtocolAdaptor<ProtocolTransportObject> expectedAdaptor = new MockProtocolAdaptorImpl();
        mockProtocolAdaptorMap.put(PROTOCOL_TYPE_NAME, expectedAdaptor);

        Field field = ProtocolPluginFactory.class.getDeclaredField("PROTOCOL_ADAPTOR_MAP");
        field.setAccessible(true);
        Field modifiersField = Field.class.getDeclaredField("modifiers");
        modifiersField.setAccessible(true);
        modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);
        field.set(null, mockProtocolAdaptorMap);

        ProtocolAdaptor<ProtocolTransportObject> actualAdaptor = ProtocolPluginFactory.getProtocolAdaptor(PROTOCOL_TYPE_NAME);
        Assertions.assertEquals(expectedAdaptor, actualAdaptor);
    }

    @Test
    public void testGetProtocolAdaptorWhenMapNotEmpty() {
        ProtocolAdaptor<ProtocolTransportObject> adaptor = ProtocolPluginFactory.getProtocolAdaptor(PROTOCOL_TYPE_NAME);
        Assertions.assertEquals(adaptor.getClass(), MockProtocolAdaptorImpl.class);
    }

    @Test
    public void testGetProtocolAdaptorWhenBothEmpty() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> ProtocolPluginFactory.getProtocolAdaptor("empty_type_name"));
    }
}
