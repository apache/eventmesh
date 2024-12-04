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
 * Single HTTP attempt event
 */
public class HttpAttemptEvent {

    public static final String PREFIX = "http-attempt-event-";

    private final int maxAttempts;

    private final AtomicInteger attempts;

    private Throwable lastException;


    public HttpAttemptEvent(int maxAttempts) {
        this.maxAttempts = maxAttempts;
        this.attempts = new AtomicInteger(0);
    }

    /**
     * Increment the attempts
     */
    public void incrementAttempts() {
        attempts.incrementAndGet();
    }

    /**
     * Update the event, incrementing the attempts and setting the last exception
     *
     * @param exception the exception to update, can be null
     */
    public void updateEvent(Throwable exception) {
        // increment the attempts
        incrementAttempts();

        // update the last exception
        lastException = exception;
    }

    /**
     * Check if the attempts are less than the maximum attempts
     *
     * @return true if the attempts are less than the maximum attempts, false otherwise
     */
    public boolean canAttempt() {
        return attempts.get() < maxAttempts;
    }

    public boolean isComplete() {
        if (attempts.get() == 0) {
            // No start yet
            return false;
        }

        // If no attempt can be made or the last exception is null, the event completed
        return !canAttempt() || lastException == null;
    }


    public int getMaxAttempts() {
        return maxAttempts;
    }

    public int getAttempts() {
        return attempts.get();
    }

    public Throwable getLastException() {
        return lastException;
    }

    /**
     * Get the limited exception message with the default limit of 256
     *
     * @return the limited exception message
     */
    public String getLimitedExceptionMessage() {
        return getLimitedExceptionMessage(256);
    }

    /**
     * Get the limited exception message with the specified limit
     *
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
