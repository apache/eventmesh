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

package org.apache.eventmesh.api.common;

import java.io.File;
import java.net.URL;
import java.util.Properties;

import org.junit.Assert;
import org.junit.Test;

public class ConfigurationWrapperTest {

    @Test
    public void testGetDefaultConfig() {
        try {
            URL resource = Thread.currentThread().getContextClassLoader().getResource("1");
            String directoryPath = resource.getPath();
            System.setProperty("confPath", directoryPath);
            Properties p = ConfigurationWrapper.getConfig("test1.properties");
            String v = (String) p.get("a");
            Assert.assertEquals(v, "2");
        } catch (Exception e) {
            Assert.fail(e.getMessage());
        }

    }

    @Test
    public void testGetSpecifiedConfig() {
        try {
            Properties p = ConfigurationWrapper.getConfig("test.properties");
            String v = (String) p.get("a");
            Assert.assertEquals(v, "1");
        } catch (Exception e) {
            Assert.fail(e.getMessage());
        }
    }
}
