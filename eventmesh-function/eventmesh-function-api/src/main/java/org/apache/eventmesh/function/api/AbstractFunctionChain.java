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

import java.util.ArrayList;
import java.util.List;

/**
 * AbstractFunctionChain is an abstract class that implements the {@link Function} interface and provides a framework
 * for chaining multiple {@link Function} instances that operate on inputs of type {@code T} and produce outputs of type
 * {@code R}. This class can be extended to create specific function chains with customized behavior for different
 * data types.
 *
 * <p>The primary purpose of this class is to allow the sequential execution of functions, where the output of one
 * function is passed as the input to the next function in the chain. The chain can be dynamically modified by adding
 * functions either at the beginning or the end of the chain.</p>
 *
 * @param <T> the type of the input to the function
 * @param <R> the type of the result of the function
 */
public abstract class AbstractFunctionChain<T, R> implements Function<T, R> {

    protected final List<Function<T, R>> functions;

    /**
     * Default constructor that initializes an empty function chain.
     */
    public AbstractFunctionChain() {
        this.functions = new ArrayList<>();
    }

    /**
     * Constructor that initializes the function chain with a given list of functions. The functions will be executed
     * in the order they are provided when the {@link #apply(Object)} method is called.
     *
     * @param functions the initial list of functions to be added to the chain
     */
    public AbstractFunctionChain(List<Function<T, R>> functions) {
        this.functions = functions;
    }

    /**
     * Adds a {@link Function} to the beginning of the chain. The function will be executed first when the
     * {@link #apply(Object)} method is called.
     *
     * @param function the function to be added to the beginning of the chain
     */
    public void addFirst(Function<T, R> function) {
        functions.add(0, function);
    }

    /**
     * Adds a {@link Function} to the end of the chain. The function will be executed in sequence after all previously
     * added functions when the {@link #apply(Object)} method is called.
     *
     * @param function the function to be added to the end of the chain
     */
    public void addLast(Function<T, R> function) {
        functions.add(function);
    }
}