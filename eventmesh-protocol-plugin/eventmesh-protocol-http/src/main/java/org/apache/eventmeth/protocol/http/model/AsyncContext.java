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

package org.apache.eventmeth.protocol.http.model;

import com.google.common.base.Preconditions;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;

public class AsyncContext<T> {

    private T request;

    private T response;

    private volatile boolean complete = Boolean.FALSE;

    private ThreadPoolExecutor asyncContextExecutor;

    public AsyncContext(T request, T response, ThreadPoolExecutor asyncContextExecutor) {
        Preconditions.checkState(request != null, "create async context err because of request is null");
        this.request = request;
        this.response = response;
        this.asyncContextExecutor = asyncContextExecutor;
    }

    public void onComplete(final T response) {
        Preconditions.checkState(Objects.nonNull(response), "response cant be null");
        this.response = response;
        this.complete = Boolean.TRUE;
    }

    public void onComplete(final T response, CompleteHandler<T> handler) {
        Preconditions.checkState(Objects.nonNull(response), "response cant be null");
        Preconditions.checkState(Objects.nonNull(handler), "handler cant be null");
        this.response = response;
        CompletableFuture.runAsync(() -> handler.onResponse(response), asyncContextExecutor);
        this.complete = Boolean.TRUE;
    }

    public boolean isComplete() {
        return complete;
    }

    public T getRequest() {
        return request;
    }

    public void setRequest(T request) {
        this.request = request;
    }

    public T getResponse() {
        return response;
    }

    public void setResponse(T response) {
        this.response = response;
    }

    public ThreadPoolExecutor getAsyncContextExecutor() {
        return asyncContextExecutor;
    }

    public void setAsyncContextExecutor(ThreadPoolExecutor asyncContextExecutor) {
        this.asyncContextExecutor = asyncContextExecutor;
    }
}
