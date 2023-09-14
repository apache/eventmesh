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

package org.apache.eventmesh.runtime.core.retry;

import java.util.concurrent.ExecutionException;

/**
 * An attempt of a call, which resulted either in a result returned by the call,
 * or in a Throwable thrown by the call.
 *
 * @param <V> The type returned by the wrapped callable.
 */
public interface Attempt<V> {

    /**
     * Returns the result of the attempt, if any.
     *
     * @return the result of the attempt
     * @throws ExecutionException if an exception was thrown by the attempt. The thrown
     *                            exception is set as the cause of the ExecutionException
     */
    public V get() throws ExecutionException;

    /**
     * Tells if the call returned a result or not
     *
     * @return <code>true</code> if the call returned a result, <code>false</code>
     *         if it threw an exception
     */
    public boolean hasResult();

    /**
     * Tells if the call threw an exception or not
     *
     * @return <code>true</code> if the call threw an exception, <code>false</code>
     *         if it returned a result
     */
    public boolean hasException();

    /**
     * Gets the result of the call
     *
     * @return the result of the call
     * @throws IllegalStateException if the call didn't return a result, but threw an exception,
     *                               as indicated by {@link #hasResult()}
     */
    public V getResult() throws IllegalStateException;

    /**
     * Gets the exception thrown by the call
     *
     * @return the exception thrown by the call
     * @throws IllegalStateException if the call didn't throw an exception,
     *                               as indicated by {@link #hasException()}
     */
    public Throwable getExceptionCause() throws IllegalStateException;

    /**
     * The number, starting from 1, of this attempt.
     *
     * @return the attempt number
     */
    public int getAttemptNumber();

    /**
     * The delay since the start of the first attempt, in milliseconds.
     *
     * @return the delay since the start of the first attempt, in milliseconds
     */
    public long getDelaySinceFirstAttempt();
}
