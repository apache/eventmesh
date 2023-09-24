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

import org.apache.eventmesh.connector.jdbc.source.SourceMateData;

import java.util.HashMap;

public final class Payload extends HashMap<String, Object> {

    public static final String AFTER_FIELD = "after";

    public static final String BEFORE_FIELD = "before";

    public static final String SOURCE = "source";

    public static final String DDL = "ddl";


    /**
     * Constructs an empty <code>HashMap</code> with the default initial capacity (16) and the default load factor (0.75).
     */
    public Payload() {
        this.put("timestamp", System.currentTimeMillis());
    }

    public Payload withSource(SourceMateData source) {
        this.put(SOURCE, source);
        return this;
    }

    public Payload withDdl(String ddl) {
        this.put(DDL, ddl);
        return this;
    }

    public Payload withCatalogChanges(CatalogChanges catalogChanges) {
        this.put("catalogChanges", catalogChanges);
        return this;
    }

    public Payload withDataChanges(DataChanges dataChanges) {
        this.put("dataChanges", dataChanges);
        return this;
    }

    public SourceMateData ofSourceMateData() {
        return (SourceMateData) super.get(SOURCE);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private final Payload payload;

        private Builder() {
            payload = new Payload();
        }

        public Builder put(String key, Object value) {
            payload.put(key, value);
            return this;
        }

        public Builder withSource(SourceMateData source) {
            payload.put(SOURCE, source);
            return this;
        }

        public Payload build() {
            return payload;
        }
    }

}
