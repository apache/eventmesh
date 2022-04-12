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

package org.apache.eventmesh.common;

import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

public class EventMeshMessageTest {

    @Test
    public void testGetProp() {
        EventMeshMessage message = createLiteMessage();
        Assert.assertEquals(2L, message.getProp().size());
    }

    @Test
    public void testSetProp() {
        EventMeshMessage message = createLiteMessage();
        Map<String, String> prop = new HashMap<>();
        prop.put("key3", "value3");
        message.setProp(prop);
        Assert.assertEquals(1L, message.getProp().size());
        Assert.assertEquals("value3", message.getProp("key3"));
    }

    @Test
    public void testAddProp() {
        EventMeshMessage message = createLiteMessage();
        message.addProp("key3", "value3");
        Assert.assertEquals(3L, message.getProp().size());
        Assert.assertEquals("value1", message.getProp("key1"));
    }

    @Test
    public void testGetPropKey() {
        EventMeshMessage message = createLiteMessage();
        Assert.assertEquals("value1", message.getProp("key1"));
    }

    @Test
    public void testRemoveProp() {
        EventMeshMessage message = createLiteMessage();
        message.removePropIfPresent("key1");
        Assert.assertEquals(1L, message.getProp().size());
        Assert.assertNull(message.getProp("key1"));
    }

    private EventMeshMessage createLiteMessage() {
        Map<String, String> prop = new HashMap<>();
        prop.put("key1", "value1");
        prop.put("key2", "value2");
        return EventMeshMessage.builder()
                .prop(prop)
                .build();
    }
}
