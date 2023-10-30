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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.google.common.collect.Maps;

public class ProtocolPluginFactoryTest {

    private static final String PROTOCOL_TYPE_NAME = "testProtocolType";

    private static final String MODIFIERS = "modifiers";

    private static final String PROTOCOL_ADAPTER_MAP = "PROTOCOL_ADAPTOR_MAP";

    @Test
    public void testGetProtocolAdaptor() throws IllegalAccessException, NoSuchFieldException {
        Map<String, ProtocolAdaptor<ProtocolTransportObject>> mockProtocolAdaptorMap =
            new ConcurrentHashMap<>(16);
        ProtocolAdaptor<ProtocolTransportObject> expectedAdaptor = new MockProtocolAdaptorImpl();
        mockProtocolAdaptorMap.put(PROTOCOL_TYPE_NAME, expectedAdaptor);

        Field field = ProtocolPluginFactory.class.getDeclaredField(PROTOCOL_ADAPTER_MAP);
        field.setAccessible(true);
        Field modifiersField = Field.class.getDeclaredField(MODIFIERS);
        modifiersField.setAccessible(true);
        modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);
        final Object originMap = field.get(null);
        field.set(null, mockProtocolAdaptorMap);

        ProtocolAdaptor<ProtocolTransportObject> actualAdaptor = ProtocolPluginFactory.getProtocolAdaptor(PROTOCOL_TYPE_NAME);
        Assertions.assertEquals(expectedAdaptor, actualAdaptor);

        field.set(null, Maps.newHashMap());
        ProtocolAdaptor<ProtocolTransportObject> adaptor = ProtocolPluginFactory.getProtocolAdaptor(PROTOCOL_TYPE_NAME);
        Assertions.assertEquals(adaptor.getClass(), MockProtocolAdaptorImpl.class);

        field.set(null, originMap);
    }

    @Test
    public void testGetProtocolAdaptorThrowsException() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> ProtocolPluginFactory.getProtocolAdaptor("empty_type_name"));
    }

    @AfterEach
    public void after() throws Exception {
        Field field = ProtocolPluginFactory.class.getDeclaredField(PROTOCOL_ADAPTER_MAP);
        field.setAccessible(true);
        Field modifiersField = Field.class.getDeclaredField(MODIFIERS);
        modifiersField.setAccessible(true);
        if (!Modifier.isFinal(field.getModifiers())) {
            modifiersField.setInt(field, field.getModifiers() | Modifier.FINAL);
        }
    }
}
