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

package org.apache.eventmesh.api;

import org.apache.eventmesh.api.consumer.Consumer;
import org.apache.eventmesh.api.producer.Producer;

/**
 * The {@code LifeCycle} defines a lifecycle interface for a OMS related service endpoint,
 * like {@link Producer}, {@link Consumer}, and so on.
 */
public interface LifeCycle {

    boolean isStarted();

    boolean isClosed();

    void start();

    void shutdown();
}
