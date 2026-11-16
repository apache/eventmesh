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
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ProtocolPluginFactoryTest {

    private static final String PROTOCOL_TYPE_NAME = "testProtocolType";

    private static final String PROTOCOL_ADAPTER_MAP = "PROTOCOL_ADAPTOR_MAP";

    @Test
    @SuppressWarnings("unchecked")
    public void testGetProtocolAdaptor() throws IllegalAccessException, NoSuchFieldException {
        Field field = ProtocolPluginFactory.class.getDeclaredField(PROTOCOL_ADAPTER_MAP);
        field.setAccessible(true);
        Map<String, ProtocolAdaptor<ProtocolTransportObject>> protocolAdaptorMap =
            (Map<String, ProtocolAdaptor<ProtocolTransportObject>>) field.get(null);

        ProtocolAdaptor<ProtocolTransportObject> expectedAdaptor = new MockProtocolAdaptorImpl();
        protocolAdaptorMap.put(PROTOCOL_TYPE_NAME, expectedAdaptor);
        try {
            ProtocolAdaptor<ProtocolTransportObject> actualAdaptor = ProtocolPluginFactory.getProtocolAdaptor(PROTOCOL_TYPE_NAME);
            Assertions.assertEquals(expectedAdaptor, actualAdaptor);

            // Without a cached adaptor, getProtocolAdaptor has to load it through SPI
            protocolAdaptorMap.remove(PROTOCOL_TYPE_NAME);
            ProtocolAdaptor<ProtocolTransportObject> adaptor = ProtocolPluginFactory.getProtocolAdaptor(PROTOCOL_TYPE_NAME);
            Assertions.assertInstanceOf(MockProtocolAdaptorImpl.class, adaptor);
        } finally {
            protocolAdaptorMap.remove(PROTOCOL_TYPE_NAME);
        }
    }

    @Test
    public void testGetProtocolAdaptorThrowsException() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> ProtocolPluginFactory.getProtocolAdaptor("empty_type_name"));
    }
}
