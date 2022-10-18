/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.apache.eventmesh.common.utils;

import org.apache.eventmesh.common.config.ConfigurationWrapperTest;

import java.util.Properties;

import org.junit.Assert;
import org.junit.Test;

public class PropertiesUtilsTest {

    private static final String PREFIX = "test.";

    @Test
    public void testGetPropertiesByPrefix() {
        Properties from = new Properties();
        from.put("a", 2);
        from.put(PREFIX + "a", 1);
        from.put(PREFIX + "b", 1.0);
        from.put(PREFIX + "c.d", "inner d");
        from.put(PREFIX + "c.f", "inner f");
        Properties to = new Properties();
        PropertiesUtils.getPropertiesByPrefix(from, to, PREFIX);

        Assert.assertEquals(3, to.size());
        Assert.assertEquals(2, ((Properties) to.get("c")).size());
    }

    @Test
    public void testLoadPropertiesWhenFileExist() {
        Properties properties = new Properties();
        String path = ConfigurationWrapperTest.class.getResource("/configuration.properties").getPath();
        try {
            PropertiesUtils.loadPropertiesWhenFileExist(properties, path);
            Assert.assertEquals(properties.get("eventMesh.server.env").toString(), "value1");
            Assert.assertEquals(properties.get("eventMesh.server.idc").toString(), "value2");
        } catch (Exception e) {
            Assert.fail();
        }
    }
}
