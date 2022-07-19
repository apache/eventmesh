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

package org.apache.eventmesh.client.common.strategy.impl;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.eventmesh.client.common.EventMeshAddress;

import java.util.Arrays;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

public class RandomLoadBalanceTest {

    private RandomLoadBalance randomLoadBalanceUnderTest;

    @Before
    public void setUp() {
        this.randomLoadBalanceUnderTest = new RandomLoadBalance();
    }

    @Test
    public void testSelect() {
        // Setup
        final EventMeshAddress eventMeshAddress = new EventMeshAddress();
        eventMeshAddress.setHost("host");
        eventMeshAddress.setPort(0);
        final List<EventMeshAddress> serverList = Arrays.asList(eventMeshAddress);
        final EventMeshAddress expectedResult = new EventMeshAddress();
        expectedResult.setHost("host");
        expectedResult.setPort(0);

        // Run the test
        final EventMeshAddress result = (EventMeshAddress) this.randomLoadBalanceUnderTest.select(serverList);

        // Verify the results
        assertThat(result).isEqualTo(expectedResult);
    }
}
