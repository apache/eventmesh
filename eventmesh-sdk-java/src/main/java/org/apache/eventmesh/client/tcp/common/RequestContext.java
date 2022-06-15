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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RequestContext {

    private static final Logger logger = LoggerFactory.getLogger(RequestContext.class);

    private Object key;
    private Package request;
    private Package response;
    private CountDownLatch latch;

    public RequestContext(Object key, Package request, CountDownLatch latch) {
        this.key = key;
        this.request = request;
        this.latch = latch;
    }

    public Object getKey() {
        return key;
    }

    public void setKey(Object key) {
        this.key = key;
    }

    public Package getRequest() {
        return request;
    }

    public void setRequest(Package request) {
        this.request = request;
    }

    public Package getResponse() {
        return response;
    }

    public void setResponse(Package response) {
        this.response = response;
    }

    public CountDownLatch getLatch() {
        return latch;
    }

    public void setLatch(CountDownLatch latch) {
        this.latch = latch;
    }

    public void finish(Package msg) {
        this.response = msg;
        latch.countDown();
    }

    public static RequestContext context(Object key, Package request, CountDownLatch latch) throws Exception {
        RequestContext c = new RequestContext(key, request, latch);
        logger.info("_RequestContext|create|key=" + key);
        return c;
    }


    public static Object key(Package request) {
        return request.getHeader().getSeq();
    }
}
