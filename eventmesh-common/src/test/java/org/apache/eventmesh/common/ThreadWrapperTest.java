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
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Assert;
import org.junit.Test;

public class ThreadWrapperTest {

    @Test
    public void getThreadName() {
        ThreadWrapper wrapper = createThreadWrapper(false);
        wrapper.start();
        Assert.assertEquals("EventMesh-Wrapper-mxsm", wrapper.thread.getName());
    }

    @Test
    public void start() {
        ThreadWrapper wrapper = createThreadWrapper(false);
        wrapper.start();
        Assert.assertTrue(wrapper.isStated());
    }

    @Test(timeout = 1000)
    public void await() {
        ThreadWrapper wrapper = createThreadWrapper(false);
        wrapper.start();
        wrapper.await(1, TimeUnit.MILLISECONDS);
        Assert.assertFalse(wrapper.hasWakeup.get());
        wrapper.wakeup();
        Assert.assertTrue(wrapper.hasWakeup.get());
        wrapper.await();
        Assert.assertFalse(wrapper.hasWakeup.get());
        wrapper.await(2, TimeUnit.MILLISECONDS);

    }

    @Test
    public void wakeup() {
    }

    @Test
    public void shutdown() {
        AtomicInteger counter = new AtomicInteger();
        ThreadWrapper wrapper = new ThreadWrapper() {

            @Override
            public String getThreadName() {
                return "EventMesh-Wrapper-mxsm";
            }

            @Override
            public void run() {
                try {
                    TimeUnit.MILLISECONDS.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                counter.set(100);
            }
        };
        wrapper.start();
        wrapper.shutdown();
        Assert.assertEquals(100, counter.get());
    }

    @Test
    public void shutdownImmediately() {
        AtomicInteger counter = new AtomicInteger();
        ThreadWrapper wrapper = new ThreadWrapper() {

            @Override
            public String getThreadName() {
                return "EventMesh-Wrapper-mxsm";
            }

            @Override
            public void run() {
                try {
                    TimeUnit.SECONDS.sleep(100);
                } catch (InterruptedException e) {
                    return;
                }
                counter.set(100);
            }
        };
        wrapper.start();
        wrapper.shutdownImmediately();
        Assert.assertEquals(0, counter.get());
    }

    @Test
    public void setDaemon() {
        ThreadWrapper threadWrapper = createThreadWrapper(true);
        threadWrapper.start();
        Assert.assertTrue(threadWrapper.thread.isDaemon());

        ThreadWrapper threadWrapper1 = createThreadWrapper(false);
        threadWrapper1.start();
        Assert.assertFalse(threadWrapper1.thread.isDaemon());
    }

    private ThreadWrapper createThreadWrapper(boolean daemon) {
        ThreadWrapper wrapper = new ThreadWrapper() {

            @Override
            public String getThreadName() {
                return "EventMesh-Wrapper-mxsm";
            }

            @Override
            public void run() {
                // nothing to do
            }
        };
        wrapper.setDaemon(daemon);
        return wrapper;
    }
}