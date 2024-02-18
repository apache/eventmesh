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

package org.apache.eventmesh.connector.jdbc.event;

/**
 * Enumeration representing different types of data change events.
 */
public enum DataChangeEventType {

    /**
     * Represents an INSERT data change event.
     */
    INSERT("I"),

    /**
     * Represents an UPDATE data change event.
     */
    UPDATE("U"),

    /**
     * Represents a DELETE data change event.
     */
    DELETE("D");

    private final String code;

    /**
     * Constructs a DataChangeEventType with the specified code.
     *
     * @param code The code representing the data change event type.
     */
    DataChangeEventType(String code) {
        this.code = code;
    }

    /**
     * Parses a DataChangeEventType from the given code.
     *
     * @param code The code to parse.
     * @return The corresponding DataChangeEventType.
     * @throws IllegalArgumentException If the provided code is unknown.
     */
    public static DataChangeEventType parseFromCode(String code) {
        for (DataChangeEventType type : DataChangeEventType.values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown DataChangeEventType code: " + code);
    }

    /**
     * Gets the code representing the DataChangeEventType.
     *
     * @return The code of the DataChangeEventType.
     */
    public String ofCode() {
        return this.code;
    }
}
