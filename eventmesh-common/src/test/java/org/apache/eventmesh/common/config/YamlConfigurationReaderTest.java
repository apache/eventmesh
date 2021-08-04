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

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.URL;
import java.util.List;

public class YamlConfigurationReaderTest {

    private YamlConfigurationReader yamlConfigurationReader;

    @Before
    public void prepare() throws IOException {
        URL resource = YamlConfigurationReaderTest.class.getClassLoader().getResource("yamlConfiguration.yml");
        yamlConfigurationReader = new YamlConfigurationReader(resource.getPath());
    }

    @Test
    public void getInt() {
        Assert.assertEquals(3, yamlConfigurationReader.getInt("eventMesh.sysid", 1));
        Assert.assertEquals(1, yamlConfigurationReader.getInt("eventMesh.sysid.xx", 1));
    }

    @Test
    public void getString() {
        Assert.assertEquals("rocketmq", yamlConfigurationReader.getString("eventMesh.connector.plugin.type", "kafka"));
        Assert.assertEquals("kafka", yamlConfigurationReader.getString("eventMesh.connector.plugin.type1", "kafka"));
        Assert.assertEquals("3", yamlConfigurationReader.getString("eventMesh.sysidStr", "kafka"));
    }

    @Test
    public void getList() {
        List<String> ipList = yamlConfigurationReader.getList("eventMesh.server.hostIp", null);
        Assert.assertNotNull(ipList);
    }
}