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

package org.apache.eventmesh.common;

import org.junit.Assert;
import org.junit.Test;

public class ThreadUtilTest {

    @Test
    public void testRandomSleep() {
        TestThread testThread = new TestThread();
        testThread.start();
        try {
            Thread.sleep(100L);
            Assert.assertTrue(testThread.getSleepTime() >= 1);
            Assert.assertTrue(testThread.getSleepTime() <= 50);
        } catch (InterruptedException ignore) {
        }
    }

    @Test
    public void testPID() {
        Assert.assertNotEquals(-1, ThreadUtil.getPID());
    }

    class TestThread extends Thread {

        private long sleepTime;

        public long getSleepTime() {
            return sleepTime;
        }

        public void run() {
            long startTime = System.currentTimeMillis();
            try {
                ThreadUtil.randomSleep(50);
            } catch (Exception ignore) {
            }
            sleepTime = System.currentTimeMillis() - startTime;
        }
    }
}
