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

package org.apache.eventmesh.function.api;

/**
 * EventMesh Interface for a function that accepts one argument and produces a result. This is a functional interface whose functional method is
 * {@link #apply(Object)}.
 *
 * <p>This interface is similar to {@link java.util.function.Function},
 * but it is specifically designed for use within the EventMesh. It allows defining custom functions to process data or events in the EventMesh. The
 * main use case is to encapsulate operations that can be passed around and applied to data or event messages in the EventMesh processing
 * pipeline.</p>
 *
 * @param <T> the type of the input to the function
 * @param <R> the type of the result of the function
 */
public interface EventMeshFunction<T, R> {

    /**
     * Applies this function to the given argument within the context of the EventMesh module. This method encapsulates the logic for processing the
     * input data and producing a result, which can be used in the EventMesh event processing pipeline.
     *
     * @param t the function argument, representing the input data or event to be processed
     * @return the function result, representing the processed output
     */
    R apply(T t);

}