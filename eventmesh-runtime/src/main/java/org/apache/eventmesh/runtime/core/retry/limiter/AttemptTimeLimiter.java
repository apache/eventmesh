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

package org.apache.eventmesh.runtime.core.retry.limiter;

import java.util.concurrent.Callable;

/**
 * A rule to wrap any single attempt in a time limit, where it will possibly be interrupted if the limit is exceeded.
 *
 * @param <V> return type of Callable
 */
public interface AttemptTimeLimiter<V> {
    /**
     * @param callable to subject to the time limit
     * @return the return of the given callable
     * @throws Exception any exception from this invocation
     */
    V call(Callable<V> callable) throws Exception;
}
