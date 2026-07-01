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

package org.apache.eventmesh.connector.jdbc;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
/**
 * DataChanges class representing changes in data associated with a JDBC connection.
 */
public class DataChanges {

    private Object after;

    private Object before;

    /**
     * The type of change.
     * <pr>
     * {@link org.apache.eventmesh.connector.jdbc.event.DataChangeEventType}
     * </pr>
     */
    private String type;

    /**
     * Constructs a DataChanges instance with 'after' and 'before' data.
     *
     * @param after  The data after the change.
     * @param before The data before the change.
     */
    public DataChanges(Object after, Object before) {
        this.after = after;
        this.before = before;
    }

    /**
     * Constructs a DataChanges instance with 'after', 'before' data, and a change type.
     *
     * @param after  The data after the change.
     * @param before The data before the change.
     * @param type   The type of change.
     */
    public DataChanges(Object after, Object before, String type) {
        this.after = after;
        this.before = before;
        this.type = type;
    }

    /**
     * Creates a new DataChanges builder.
     *
     * @return The DataChanges builder.
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * Builder class for constructing DataChanges instances.
     */
    public static class Builder {

        private String type;
        private Object after;
        private Object before;

        /**
         * Sets the change type in the builder.
         *
         * @param type The type of change.
         * @return The DataChanges builder.
         */
        public Builder withType(String type) {
            this.type = type;
            return this;
        }

        /**
         * Sets the 'after' data in the builder.
         *
         * @param after The data after the change.
         * @return The DataChanges builder.
         */
        public Builder withAfter(Object after) {
            this.after = after;
            return this;
        }

        /**
         * Sets the 'before' data in the builder.
         *
         * @param before The data before the change.
         * @return The DataChanges builder.
         */
        public Builder withBefore(Object before) {
            this.before = before;
            return this;
        }

        /**
         * Builds the DataChanges instance.
         *
         * @return The constructed DataChanges.
         */
        public DataChanges build() {
            return new DataChanges(after, before, type);
        }
    }
}
