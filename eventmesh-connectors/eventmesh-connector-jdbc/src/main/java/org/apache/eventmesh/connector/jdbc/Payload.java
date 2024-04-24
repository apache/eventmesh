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

import lombok.Getter;
import lombok.Setter;

/**
 * Payload class representing the data associated with a JDBC connection.
 */
public final class Payload {

    /**
     * Field name for the 'after' payload.
     */
    public static final String AFTER_FIELD = "after";

    /**
     * Field name for the 'before' payload.
     */
    public static final String BEFORE_FIELD = "before";

    /**
     * Field name for the 'source' payload.
     */
    public static final String SOURCE = "source";

    /**
     * Field name for the 'payload.before' payload.
     */
    public static final String PAYLOAD_BEFORE = "payload.before";

    /**
     * Field name for the 'payload.after' payload.
     */
    public static final String PAYLOAD_AFTER = "payload.after";

    @Getter
    @Setter
    private SourceMateData source;

    // Source connector's original DDL script
    @Getter
    @Setter
    private String ddl;

    @Getter
    @Setter
    private CatalogChanges catalogChanges;

    @Getter
    @Setter
    private DataChanges dataChanges;

    @Getter
    @Setter
    private long timestamp;

    /**
     * Constructs an empty Payload with the default initial capacity (16) and the default load factor (0.75).
     */
    public Payload() {
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * Sets the 'source' field in the payload.
     *
     * @param source The SourceMateData to set.
     * @return The Payload instance.
     */
    public <S extends SourceMateData> Payload withSource(S source) {
        this.source = source;
        return this;
    }

    /**
     * Sets the 'ddl' field in the payload.
     *
     * @param ddl The DDL string to set.
     * @return The Payload instance.
     */
    public Payload withDdl(String ddl) {
        this.ddl = ddl;
        return this;
    }

    /**
     * Sets the 'catalogChanges' field in the payload.
     *
     * @param catalogChanges The CatalogChanges to set.
     * @return The Payload instance.
     */
    public Payload withCatalogChanges(CatalogChanges catalogChanges) {
        this.catalogChanges = catalogChanges;
        return this;
    }

    /**
     * Sets the 'dataChanges' field in the payload.
     *
     * @param dataChanges The DataChanges to set.
     * @return The Payload instance.
     */
    public Payload withDataChanges(DataChanges dataChanges) {
        this.dataChanges = dataChanges;
        return this;
    }

    /**
     * Retrieves the 'source' field from the payload.
     *
     * @return The SourceMateData.
     */
    public SourceMateData ofSourceMateData() {
        return getSource();
    }

    /**
     * Retrieves the 'catalogChanges' field from the payload.
     *
     * @return The CatalogChanges.
     */
    public CatalogChanges ofCatalogChanges() {
        return getCatalogChanges();
    }

    /**
     * Retrieves the 'dataChanges' field from the payload.
     *
     * @return The DataChanges.
     */
    public DataChanges ofDataChanges() {
        return getDataChanges();
    }

    /**
     * Retrieves the 'ddl' field from the payload.
     *
     * @return The DDL string.
     */
    public String ofDdl() {
        return getDdl();
    }

    /**
     * Builder class for constructing Payload instances.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder class for constructing Payload instances.
     */
    public static class Builder {

        private final Payload payload;

        private Builder() {
            payload = new Payload();
        }

        /**
         * Sets the 'source' field in the payload.
         *
         * @param source The SourceMateData to set.
         * @return The Builder instance.
         */
        public Builder withSource(SourceMateData source) {
            payload.withSource(source);
            return this;
        }

        /**
         * Builds the Payload instance.
         *
         * @return The constructed Payload.
         */
        public Payload build() {
            return payload;
        }
    }
}
