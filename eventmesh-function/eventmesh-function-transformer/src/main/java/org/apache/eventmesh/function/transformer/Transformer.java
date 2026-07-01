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

package org.apache.eventmesh.function.transformer;

import org.apache.eventmesh.common.exception.EventMeshException;
import org.apache.eventmesh.function.api.EventMeshFunction;

import com.fasterxml.jackson.core.JsonProcessingException;

/**
 * EventMesh transformer interface, specified transformer implementation includes:
 * 1. Constant
 * 2. Original
 * 3. Template
 */
public interface Transformer extends EventMeshFunction<String, String> {

    String transform(String json) throws JsonProcessingException;

    @Override
    default String apply(String content) {
        try {
            return transform(content);
        } catch (JsonProcessingException e) {
            throw new EventMeshException("Failed to transform content", e);
        }
    }

}
