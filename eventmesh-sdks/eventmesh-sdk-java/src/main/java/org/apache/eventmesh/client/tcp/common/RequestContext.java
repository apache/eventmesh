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
import org.apache.eventmesh.common.utils.LogUtils;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RequestContext {

    private Object key;
    private Package request;
    private final CompletableFuture<Package> future = new CompletableFuture<>();

    public RequestContext(final Object key, final Package request) {
        this.key = key;
        this.request = request;
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

    public CompletableFuture<Package> future() {
        return this.future;
    }

    public Package getResponse(long timeout, TimeUnit timeUnit) throws ExecutionException, InterruptedException, TimeoutException {
        return this.future.get(timeout, timeUnit);
    }

    public Package getResponse(long timeout) throws ExecutionException, InterruptedException, TimeoutException {
        return this.future.get(timeout, TimeUnit.MILLISECONDS);
    }

    public void finish(final Package msg) {
        this.future.complete(msg);
    }

    public static RequestContext context(final Object key, final Package request) throws Exception {
        final RequestContext context = new RequestContext(key, request);
        LogUtils.info(log, "_RequestContext|create|key={}", key);
        return context;
    }

    public static Object key(final Package request) {
        return request.getHeader().getSeq();
    }
}
