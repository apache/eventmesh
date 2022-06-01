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

package org.apache.eventmesh.common.config;

import java.io.File;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class ConfigurationWrapperTest {

    private ConfigurationWrapper wraper;

    @Before
    public void before() {
        String file = ConfigurationWrapperTest.class.getResource("/configuration.properties").getFile();
        File f = new File(file);
        wraper = new ConfigurationWrapper(f.getParent(), f.getName(), false);
    }

    @Test
    public void testGetProp() {
        Assert.assertEquals("value1", wraper.getProp("eventMesh.server.env"));
        Assert.assertEquals("value2", wraper.getProp("eventMesh.server.idc"));
    }
}
