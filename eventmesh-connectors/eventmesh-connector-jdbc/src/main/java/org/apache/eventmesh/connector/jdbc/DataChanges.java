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

@Data
public class DataChanges {

    private Object after;

    private Object before;

    private String type;

    public DataChanges(Object after, Object before) {
        this.after = after;
        this.before = before;
    }

    public DataChanges(Object after, Object before, String type) {
        this.after = after;
        this.before = before;
        this.type = type;
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static class Builder {

        private String type;
        private Object after;
        private Object before;

        public Builder withType(String type) {
            this.type = type;
            return this;
        }

        public Builder withAfter(Object after) {
            this.after = after;
            return this;
        }

        public Builder withBefore(Object before) {
            this.before = before;
            return this;
        }

        public DataChanges build() {
            return new DataChanges(after, before, type);
        }
    }
}
