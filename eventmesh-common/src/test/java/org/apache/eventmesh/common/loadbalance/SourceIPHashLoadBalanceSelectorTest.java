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

package org.apache.eventmesh.common.loadbalance;

import org.apache.eventmesh.common.utils.CommonStringUtils;

import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SourceIPHashLoadBalanceSelectorTest {

    private SourceIPHashLoadBalanceSelector<String> loadBalanceSelector;

    private List<String> servers;

    private String client1Key;

    private String client2Key;

    @Before
    public void init() {
        servers = Arrays.asList(new String[]{
            "192.168.1.10", "192.168.1.11",
            "192.168.1.12", "192.168.1.13",
            "192.168.1.14", "192.168.1.15"
        });
        client1Key = "192.168.1.1-1-tester1-TLSv1.2";
        client2Key = "192.168.1.2-1-tester2-TLSv1.2";
        loadBalanceSelector = new SourceIPHashLoadBalanceSelector<>(servers, client1Key);
    }

    @Test
    public void testSelect() {
        String target1 = loadBalanceSelector.select();
        String target2 = loadBalanceSelector.select();
        String target3 = loadBalanceSelector.select();
        Assert.assertTrue(CommonStringUtils.equalsAll(target1, target2, target3));

        loadBalanceSelector = new SourceIPHashLoadBalanceSelector<>(servers, client2Key);
        String target4 = loadBalanceSelector.select();
        Assert.assertFalse(StringUtils.equals(target1, target4));
    }

    @Test
    public void testType() {
        Assert.assertEquals(LoadBalanceType.SOURCE_IP_HASH, loadBalanceSelector.getType());
    }
}
