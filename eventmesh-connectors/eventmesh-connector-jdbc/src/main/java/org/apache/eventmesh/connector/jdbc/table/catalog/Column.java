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

package org.apache.eventmesh.connector.jdbc.table.catalog;

import org.apache.eventmesh.connector.jdbc.table.type.EventMeshDataType;

import java.io.Serializable;
import java.sql.JDBCType;
import java.sql.Types;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Column of {@link TableSchema}.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Column<Col extends Column> implements Serializable {

    /**
     * Name of the column
     */
    protected String name;

    /**
     * Data type of the column
     */
    @JsonSerialize(using = EventMeshDataTypeJsonSerializer.class)
    @JsonDeserialize(using = EventMeshDataTypeJsonDeserializer.class)
    protected EventMeshDataType dataType;

    /**
     * {@link Types JDBC type}
     */
    protected JDBCType jdbcType;

    /**
     * Length of the column
     */
    protected Long columnLength;

    /**
     * Decimal point of the column
     */
    protected Integer decimal;

    /**
     * Indicates if the column can be null or not
     */
    protected boolean notNull = false;

    /**
     * Comment for the column
     */
    protected String comment;

    /**
     * Default value for the column
     */
    protected Object defaultValue;

    @JsonIgnore
    protected String defaultValueExpression;

    // order of the column in the table
    protected int order = 1;

    protected String charsetName;

    // Use wrapper types to reduce data transmission during serialization
    protected Boolean autoIncremented;

    protected Boolean generated;

    protected String collationName;

    protected List<String> enumValues;

    // for mysql: varchar or json
    protected String nativeType;

    protected Options options;

    public Column(String name, EventMeshDataType dataType, JDBCType jdbcType, Long columnLength, Integer decimal, boolean notNull, String comment,
        Object defaultValue, String defaultValueExpression, int order, String charsetName, boolean autoIncremented, boolean generated,
        String collationName) {
        this.name = name;
        this.dataType = dataType;
        this.jdbcType = jdbcType;
        this.columnLength = columnLength;
        this.decimal = decimal;
        this.notNull = notNull;
        this.comment = comment;
        this.defaultValue = defaultValue;
        this.defaultValueExpression = defaultValueExpression;
        this.order = order;
        this.charsetName = charsetName;
        this.autoIncremented = autoIncremented;
        this.generated = generated;
        this.collationName = collationName;
    }

    private Column(Builder builder) {
        this.name = builder.name;
        this.dataType = builder.dataType;
        this.jdbcType = builder.jdbcType;
        this.columnLength = builder.columnLength;
        this.decimal = builder.decimal;
        this.notNull = builder.notNull;
        this.comment = builder.comment;
        this.defaultValue = builder.defaultValue;
        this.defaultValueExpression = builder.defaultValueExpression;
        this.order = builder.order;
        this.charsetName = builder.charsetName;
        this.autoIncremented = builder.autoIncremented;
        this.generated = builder.generated;
        this.collationName = builder.collationName;
        this.enumValues = builder.enumValues;
        this.nativeType = builder.nativeType;
        this.options = builder.options;
    }

    public boolean isAutoIncremented() {
        return Optional.ofNullable(this.autoIncremented).orElse(false);
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * creates a clone of the Column
     *
     * @return clone of column
     */
    public Col clone() {
        return null;
    }

    /**
     * Builder for the Column class
     */
    public static class Builder {

        protected String name;
        protected EventMeshDataType dataType;
        protected JDBCType jdbcType;
        protected Long columnLength;
        protected Integer decimal;
        protected boolean notNull = false;
        protected String comment;
        protected Object defaultValue;
        protected String defaultValueExpression;
        protected int order = 1;
        protected String charsetName;
        protected Boolean autoIncremented;
        protected Boolean generated;
        protected String collationName;
        protected List<String> enumValues;
        // for mysql: varchar or json
        protected String nativeType;

        protected Options options;

        public Builder withName(String name) {
            this.name = name;
            return this;
        }

        public Builder withDataType(EventMeshDataType dataType) {
            this.dataType = dataType;
            return this;
        }

        public Builder withJdbcType(JDBCType jdbcType) {
            this.jdbcType = jdbcType;
            return this;
        }

        public Builder withColumnLength(Long columnLength) {
            this.columnLength = columnLength;
            return this;
        }

        public Builder withDecimal(Integer decimal) {
            this.decimal = decimal;
            return this;
        }

        public Builder withNotNull(boolean notNull) {
            this.notNull = notNull;
            return this;
        }

        public Builder withComment(String comment) {
            this.comment = comment;
            return this;
        }

        public Builder withDefaultValue(Object defaultValue) {
            this.defaultValue = defaultValue;
            return this;
        }

        public Builder withDefaultValueExpression(String defaultValueExpression) {
            this.defaultValueExpression = defaultValueExpression;
            return this;
        }

        public Builder withOrder(int order) {
            this.order = order;
            return this;
        }

        public Builder withCharsetName(String charsetName) {
            this.charsetName = charsetName;
            return this;
        }

        public Builder withAutoIncremented(boolean autoIncremented) {
            this.autoIncremented = autoIncremented;
            return this;
        }

        public Builder withGenerated(boolean generated) {
            this.generated = generated;
            return this;
        }

        public Builder withCollationName(String collationName) {
            this.collationName = collationName;
            return this;
        }

        public Builder withEnumValues(List<String> enumValues) {
            this.enumValues = enumValues;
            return this;
        }

        public Builder withNativeType(String nativeType) {
            this.nativeType = nativeType;
            return this;
        }

        public Builder withOptions(Options options) {
            this.options = options;
            return this;
        }

        /**
         * Builds the Column instance.
         *
         * @return Column instance
         */
        public Column build() {
            return new Column(this);
        }
    }

}
