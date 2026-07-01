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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

class ThreadUtilsTest {

    @Test
    void testRandomPauseBetweenMinAndMax() {

        long min = 1000;
        long max = 5000;

        long start = System.currentTimeMillis();
        ThreadUtils.randomPause(min, max, TimeUnit.MILLISECONDS);
        long end = System.currentTimeMillis();

        long pause = end - start;

        assertTrue(pause >= min && pause <= max, "Pause time should be between min and max");
    }

    @Test
    void testRandomPauseWithInterruption() {

        Thread.currentThread().interrupt();
        ThreadUtils.randomPause(1000, 2000, TimeUnit.MILLISECONDS);
        assertTrue(Thread.currentThread().isInterrupted());
    }

    @Test
    void testDeprecatedSleep() {

        ThreadUtils.sleep(1000);
        assertTrue(true, "Method should execute without any exception");
    }

    @Test
    void testSleepWithTimeOutAndTimeUnit() throws InterruptedException {

        ThreadUtils.sleepWithThrowException(5000, TimeUnit.MILLISECONDS);
        assertTrue(true, "Method should execute without any exception");
    }

    @Test
    void testSleepWithNullTimeUnit() throws InterruptedException {

        ThreadUtils.sleepWithThrowException(5000, null);
        assertTrue(true, "Method should not throw any exception with null TimeUnit");
    }

    @Test
    void testSleepWithThrowExceptionInterruption() {
        Thread.currentThread().interrupt();

        assertThrows(InterruptedException.class, () -> {
            ThreadUtils.sleepWithThrowException(5000, TimeUnit.MILLISECONDS);
        });
    }

    @Test
    void testGetPIDWithRealProcessId() {

        long pid = ThreadUtils.getPID();
        assertTrue(pid > 0);

        long cashedPId = ThreadUtils.getPID();
        assertEquals(pid, cashedPId);
    }

    @Test
    void testGetPIDWithMultiThread() throws InterruptedException {

        final long[] pid1 = new long[1];
        final long[] pid2 = new long[1];

        Thread thread1 = new Thread(() -> {
            pid1[0] = ThreadUtils.getPID();
            assertTrue(pid1[0] > 0);
        });

        Thread thread2 = new Thread(() -> {
            pid2[0] = ThreadUtils.getPID();
            assertTrue(pid2[0] > 0);
        });

        thread1.start();
        thread2.start();

        thread1.join();
        thread2.join();

        assertEquals(pid1[0], pid2[0]);
    }
}