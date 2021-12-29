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

package org.apache.eventmesh.spi;

import org.junit.Assert;
import org.junit.Test;

import org.apache.eventmesh.spi.example.TestPrototypeExtension;
import org.apache.eventmesh.spi.example.TestSingletonExtension;

public class EventMeshExtensionFactoryTest {

    @Test
    public void testGetSingletonExtension() {
        TestSingletonExtension extensionA = EventMeshExtensionFactory.getExtension(TestSingletonExtension.class, "singletonExtension");
        TestSingletonExtension extensionB = EventMeshExtensionFactory.getExtension(TestSingletonExtension.class, "singletonExtension");
        Assert.assertSame(extensionA, extensionB);
    }

    @Test
    public void testGetPrototypeExtension() {
        TestPrototypeExtension prototypeExtensionA = EventMeshExtensionFactory.getExtension(TestPrototypeExtension.class, "prototypeExtension");
        TestPrototypeExtension prototypeExtensionB = EventMeshExtensionFactory.getExtension(TestPrototypeExtension.class, "prototypeExtension");
        Assert.assertNotSame(prototypeExtensionA, prototypeExtensionB);
    }
}