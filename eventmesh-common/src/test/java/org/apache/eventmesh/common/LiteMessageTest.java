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

import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class LiteMessageTest {

    @Test
    public void testGetProp() {
        LiteMessage message = createLiteMessage();
        Assert.assertEquals(2L, message.getProp().size());
    }

    @Test
    public void testSetProp() {
        LiteMessage message = createLiteMessage();
        Map<String, String> prop = new HashMap<>();
        prop.put("key3", "value3");
        message.setProp(prop);
        Assert.assertEquals(1L, message.getProp().size());
        Assert.assertEquals("value3", message.getPropKey("key3"));
    }

    @Test
    public void testAddProp() {
        LiteMessage message = createLiteMessage();
        message.addProp("key3", "value3");
        Assert.assertEquals(3L, message.getProp().size());
        Assert.assertEquals("value1", message.getPropKey("key1"));
    }

    @Test
    public void testGetPropKey() {
        LiteMessage message = createLiteMessage();
        Assert.assertEquals("value1", message.getPropKey("key1"));
    }

    @Test
    public void testRemoveProp() {
        LiteMessage message = createLiteMessage();
        message.removeProp("key1");
        Assert.assertEquals(1L, message.getProp().size());
        Assert.assertNull(message.getPropKey("key1"));
    }

    private LiteMessage createLiteMessage() {
        LiteMessage result = new LiteMessage();
        Map<String, String> prop = new HashMap<>();
        prop.put("key1", "value1");
        prop.put("key2", "value2");
        result.setProp(prop);
        return result;
    }
}
