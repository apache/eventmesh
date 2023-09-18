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

package org.apache.eventmesh.connector.jdbc.table.type;

/**
 * An enum representing the different modes in which a row can be handled.
 */
public enum RowHandleMode {

    INSERT("+I", (byte) 1),
    UPDATE_BEFORE("-UB", (byte) 2),
    UPDATE_AFTER("+UA", (byte) 3),
    DELETE("-D", (byte) 1),
    ;

    private final String shortCut;

    private final byte value;

    /**
     * Constructor for RowHandleMode.
     *
     * @param shortCut a string representing the shorthand for the row handle mode.
     * @param value    a byte representing the value of the row handle mode.
     */
    RowHandleMode(String shortCut, byte value) {
        this.shortCut = shortCut;
        this.value = value;
    }

    /**
     * Returns the shorthand for the row handle mode.
     *
     * @return a string representing the shorthand for the row handle mode.
     */
    public String toShortCut() {
        return shortCut;
    }

    /**
     * Returns the value of the row handle mode.
     *
     * @return a byte representing the value of the row handle mode.
     */
    public byte toValue() {
        return value;
    }

    /**
     * Returns the row handle mode corresponding to the given byte value.
     *
     * @param value a byte representing the value of the row handle mode.
     * @return the row handle mode corresponding to the given byte value.
     * @throws UnsupportedOperationException if the byte value is not supported.
     */
    public static RowHandleMode fromByteValue(byte value) {
        switch (value) {
            case 0:
                return INSERT;
            case 1:
                return UPDATE_BEFORE;
            case 2:
                return UPDATE_AFTER;
            case 3:
                return DELETE;
            default:
                throw new UnsupportedOperationException(
                        "Unsupported byte value '" + value + "' for row handle mode.");
        }
    }
}
