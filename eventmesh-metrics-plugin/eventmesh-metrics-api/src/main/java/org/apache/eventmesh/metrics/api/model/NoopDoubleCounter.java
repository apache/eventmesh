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

package org.apache.eventmesh.metrics.api.model;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleCounter;
import io.opentelemetry.context.Context;

public class NoopDoubleCounter implements DoubleCounter {

    /**
     * Records a value.
     *
     * <p>Note: This may use {@code Context.current()} to pull the context associated with this
     * measurement.
     *
     * @param value The increment amount. MUST be non-negative.
     */
    @Override
    public void add(double value) {

    }

    /**
     * Records a value with a set of attributes.
     *
     * <p>Note: This may use {@code Context.current()} to pull the context associated with this
     * measurement.
     *
     * @param value      The increment amount. MUST be non-negative.
     * @param attributes A set of attributes to associate with the value.
     */
    @Override
    public void add(double value, Attributes attributes) {

    }

    /**
     * Records a value with a set of attributes.
     *
     * @param value      The increment amount. MUST be non-negative.
     * @param attributes A set of attributes to associate with the value.
     * @param context    The explicit context to associate with this measurement.
     */
    @Override
    public void add(double value, Attributes attributes, Context context) {

    }
}
