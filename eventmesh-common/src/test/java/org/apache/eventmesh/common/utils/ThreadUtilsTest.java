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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class ThreadUtilsTest {

    @Test
    public void testRandomPauseDurationWithinBounds() throws InterruptedException {
        long min = 100;
        long max = 200;

        long startTime = System.currentTimeMillis();

        ThreadUtils.randomPause(min, max);

        long endTime = System.currentTimeMillis();

        long duration = endTime - startTime;

        assertTrue(duration >= min && duration <= max+20,
            "The pause duration should be within the specified bounds, allowing a small margin for timing inaccuracies.");
    }

    @Test
    public void testGetPIDReturnsPositiveValue() {

        long pid = ThreadUtils.getPID();

        assertTrue(pid > 0, "The PID should be a positive value.");
    }

    @Test
    public void testGetPIDReturnsConsistentValue() {

        long firstCallPID = ThreadUtils.getPID();
        long secondCallPID = ThreadUtils.getPID();

        assertEquals(firstCallPID, secondCallPID, "The PID should remain consistent across calls.");
    }
}