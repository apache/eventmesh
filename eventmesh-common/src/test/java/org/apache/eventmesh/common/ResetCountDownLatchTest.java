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

import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.Test;

public class ResetCountDownLatchTest {

    @Test
    public void testConstructorParameterError() {
        try {
            new ResetCountDownLatch(-1);
        } catch (IllegalArgumentException e) {
            Assert.assertEquals(e.getMessage(), "count must be greater than or equal to 0");
        }
        ResetCountDownLatch resetCountDownLatch = new ResetCountDownLatch(1);
        Assert.assertEquals(1, resetCountDownLatch.getCount());
    }

    @Test
    public void testAwaitTimeout() throws InterruptedException {
        ResetCountDownLatch latch = new ResetCountDownLatch(1);
        boolean await = latch.await(5, TimeUnit.MILLISECONDS);
        Assert.assertFalse(await);
        latch.countDown();
        await = latch.await(5, TimeUnit.MILLISECONDS);
        Assert.assertTrue(await);
    }

    @Test(timeout = 1000)
    public void testCountDownAndGetCount() throws InterruptedException {
        int count = 2;
        ResetCountDownLatch resetCountDownLatch = new ResetCountDownLatch(count);
        Assert.assertEquals(count, resetCountDownLatch.getCount());
        resetCountDownLatch.countDown();
        Assert.assertEquals(count - 1, resetCountDownLatch.getCount());
        resetCountDownLatch.countDown();
        resetCountDownLatch.await();
        Assert.assertEquals(0, resetCountDownLatch.getCount());
    }

    @Test
    public void testReset() throws InterruptedException {
        int count = 2;
        ResetCountDownLatch resetCountDownLatch = new ResetCountDownLatch(count);
        resetCountDownLatch.countDown();
        Assert.assertEquals(count - 1, resetCountDownLatch.getCount());
        resetCountDownLatch.reset();
        Assert.assertEquals(count, resetCountDownLatch.getCount());
        resetCountDownLatch.countDown();
        resetCountDownLatch.countDown();
        resetCountDownLatch.await();
        Assert.assertEquals(0, resetCountDownLatch.getCount());
        resetCountDownLatch.countDown();
        Assert.assertEquals(0, resetCountDownLatch.getCount());
        resetCountDownLatch.reset();
        resetCountDownLatch.countDown();
        Assert.assertEquals(1, resetCountDownLatch.getCount());

    }
}