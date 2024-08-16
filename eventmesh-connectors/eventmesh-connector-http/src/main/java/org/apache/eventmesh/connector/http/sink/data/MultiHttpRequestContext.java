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

package org.apache.eventmesh.connector.http.sink.data;

import java.util.concurrent.atomic.AtomicInteger;


/**
 * Multi HTTP request context
 */
public class MultiHttpRequestContext {

    public static final String NAME = "multi-http-request-context";

    /**
     * The remaining requests to be processed.
     */
    private final AtomicInteger remainingRequests;

    /**
     * The last failed event.
     * If there are no retries or retries are not enabled, it will be null.
     * If retries occur but still fail, it will be logged, and only the last one will be retained.
     */
    private HttpRetryEvent lastFailedEvent;

    public MultiHttpRequestContext(int remainingEvents) {
        this.remainingRequests = new AtomicInteger(remainingEvents);
    }

    /**
     * Decrement the remaining requests by 1.
     */
    public void decrementRemainingRequests() {
        remainingRequests.decrementAndGet();
    }

    public int getRemainingRequests() {
        return remainingRequests.get();
    }

    public HttpRetryEvent getLastFailedEvent() {
        return lastFailedEvent;
    }

    public void setLastFailedEvent(HttpRetryEvent lastFailedEvent) {
        this.lastFailedEvent = lastFailedEvent;
    }
}
