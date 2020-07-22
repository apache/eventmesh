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

package cn.webank.defibus.client.impl.producer;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.rocketmq.common.message.Message;

public class RRResponseFuture {
    private CountDownLatch countDownLatch = new CountDownLatch(1);
    private volatile Message respMsg = null;
    private final RRCallback rrCallback;
    private long expiredTime = System.currentTimeMillis();
    private AtomicBoolean release = new AtomicBoolean(false);

    public RRResponseFuture() {
        this.rrCallback = null;
    }

    public RRResponseFuture(RRCallback rrCallback) {
        this.rrCallback = rrCallback;
    }

    public RRResponseFuture(RRCallback rrCallback, long timeout) {
        this.rrCallback = rrCallback;
        this.expiredTime += timeout;
    }

    public void putResponse(final Message respMsg) {
        this.respMsg = respMsg;
        this.countDownLatch.countDown();
    }

    public Message waitResponse(long timeout) throws InterruptedException {
        this.countDownLatch.await(timeout, TimeUnit.MILLISECONDS);
        return this.respMsg;
    }

    public RRCallback getRrCallback() {
        return rrCallback;
    }

    public long getExpiredTime() {
        return expiredTime;
    }

    public boolean release() {
        return release.getAndSet(true);
    }
}
