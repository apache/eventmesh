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

package org.apache.eventmesh.client.tcp.common;

import org.apache.eventmesh.common.protocol.tcp.Package;

import java.util.concurrent.CountDownLatch;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RequestContext {

    private transient Object key;
    private transient Package request;
    private transient Package response;
    private transient CountDownLatch latch;

    public RequestContext(final Object key, final Package request, final CountDownLatch latch) {
        this.key = key;
        this.request = request;
        this.latch = latch;
    }

    public static RequestContext context(final Object key, final Package request, final CountDownLatch latch) throws Exception {
        final RequestContext c = new RequestContext(key, request, latch);
        if (log.isInfoEnabled()) {
            log.info("_RequestContext|create|key={}", key);
        }
        return c;
    }

    public static Object key(final Package request) {
        return request.getHeader().getSeq();
    }

    public Object getKey() {
        return key;
    }

    public void setKey(final Object key) {
        this.key = key;
    }

    public Package getRequest() {
        return request;
    }

    public void setRequest(final Package request) {
        this.request = request;
    }

    public Package getResponse() {
        return response;
    }

    public void setResponse(final Package response) {
        this.response = response;
    }

    public CountDownLatch getLatch() {
        return latch;
    }

    public void setLatch(final CountDownLatch latch) {
        this.latch = latch;
    }

    public void finish(final Package msg) {
        this.response = msg;
        latch.countDown();
    }
}
