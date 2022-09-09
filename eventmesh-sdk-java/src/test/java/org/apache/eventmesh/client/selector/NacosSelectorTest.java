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

package org.apache.eventmesh.client.selector;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

import org.junit.Assert;
import org.junit.Test;

import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;

public class NacosSelectorTest {

    @Test
    public void testSelectOne() throws Exception {
        NamingService namingService = mock(NamingService.class);
        Instance instance = new Instance();
        instance.setHealthy(true);
        instance.setIp("127.0.0.1");
        instance.setPort(11024);
        when(namingService.selectOneHealthyInstance(anyString())).thenReturn(instance);

        NacosSelector selector = new NacosSelector();
        selector.setNamingService(namingService);
        ServiceInstance serviceInstance = selector.selectOne(anyString());
        Assert.assertEquals(serviceInstance.getHost(), "127.0.0.1");
    }
}
