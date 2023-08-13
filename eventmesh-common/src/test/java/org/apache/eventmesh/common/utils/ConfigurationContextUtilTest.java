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

package org.apache.eventmesh.common.utils;

import org.apache.eventmesh.common.config.CommonConfiguration;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class ConfigurationContextUtilTest {

    private CommonConfiguration grpcConfig;

    @Before
    public void setUp() {
        grpcConfig = new CommonConfiguration();
        grpcConfig.setEventMeshName("grpc");
        ConfigurationContextUtil.putIfAbsent(ConfigurationContextUtil.GRPC, grpcConfig);
    }

    @Test
    public void testPutIfAbsent() {
        CommonConfiguration tcpConfig = new CommonConfiguration();
        tcpConfig.setEventMeshName("tpc");
        ConfigurationContextUtil.putIfAbsent(ConfigurationContextUtil.TCP, tcpConfig);
        CommonConfiguration get = ConfigurationContextUtil.get(ConfigurationContextUtil.TCP);
        Assert.assertNotNull(get);
        Assert.assertEquals(tcpConfig, get);
        CommonConfiguration newGrpc = new CommonConfiguration();
        newGrpc.setEventMeshName("newGrpc");
        ConfigurationContextUtil.putIfAbsent(ConfigurationContextUtil.GRPC, newGrpc);
        CommonConfiguration getGrpc = ConfigurationContextUtil.get(ConfigurationContextUtil.GRPC);
        Assert.assertNotNull(getGrpc);
        Assert.assertEquals(grpcConfig, getGrpc);
        Assert.assertNotEquals(newGrpc, getGrpc);
    }

    @Test
    public void testGet() {
        CommonConfiguration result = ConfigurationContextUtil.get(ConfigurationContextUtil.GRPC);
        Assert.assertNotNull(result);
        Assert.assertEquals(grpcConfig, result);
    }

    @Test
    public void testClear() {
        CommonConfiguration result0 = ConfigurationContextUtil.get(ConfigurationContextUtil.GRPC);
        Assert.assertNotNull(result0);
        ConfigurationContextUtil.clear();
        CommonConfiguration result = ConfigurationContextUtil.get(ConfigurationContextUtil.GRPC);
        Assert.assertNull(result);
    }
}
