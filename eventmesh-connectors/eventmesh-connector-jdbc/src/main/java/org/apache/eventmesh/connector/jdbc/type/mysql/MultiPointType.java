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

package org.apache.eventmesh.connector.jdbc.type.mysql;

import org.apache.eventmesh.connector.jdbc.dialect.DatabaseDialect;
import org.apache.eventmesh.connector.jdbc.table.catalog.Column;

import java.util.Arrays;
import java.util.List;

public class MultiPointType extends SpatialDataType {

    public static final MultiPointType INSTANCE = new MultiPointType();

    public MultiPointType() {
        super("MULTIPOINT");
    }

    @Override
    public List<String> ofRegistrationKeys() {
        return Arrays.asList(getName(), "multipoint");
    }

    @Override
    public String getTypeName(Column<?> column) {
        return "multipoint";
    }

    @Override
    public String getQueryBindingWithValue(DatabaseDialect<?> databaseDialect, Column<?> column) {
        return "ST_MPointFromWKB(?,?)";
    }
}
