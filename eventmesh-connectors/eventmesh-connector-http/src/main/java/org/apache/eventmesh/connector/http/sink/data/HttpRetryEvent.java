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

import lombok.Data;

/**
 * Single HTTP retry event
 */
@Data
public class HttpRetryEvent {

    public static final String PREFIX = "http-retry-event-";

    private String parentId;

    private int maxRetries;

    private int currentRetries;

    private Throwable lastException;

    /**
     * Increase the current retries by 1
     */
    public void increaseCurrentRetries() {
        this.currentRetries++;
    }

    /**
     * Check if the current retries is greater than or equal to the max retries
     * @return true if the current retries is greater than or equal to the max retries
     */
    public boolean isMaxRetriesReached() {
        return this.currentRetries >= this.maxRetries;
    }

    /**
     * Get the limited exception message with the default limit of 256
     * @return the limited exception message
     */
    public String getLimitedExceptionMessage() {
        return getLimitedExceptionMessage(256);
    }

    /**
     * Get the limited exception message with the specified limit
     * @param maxLimit the maximum limit of the exception message
     * @return the limited exception message
     */
    public String getLimitedExceptionMessage(int maxLimit) {
        if (lastException == null) {
            return "";
        }
        String message = lastException.getMessage();
        if (message == null) {
            return "";
        }
        if (message.length() > maxLimit) {
            return message.substring(0, maxLimit);
        }
        return message;
    }

}
