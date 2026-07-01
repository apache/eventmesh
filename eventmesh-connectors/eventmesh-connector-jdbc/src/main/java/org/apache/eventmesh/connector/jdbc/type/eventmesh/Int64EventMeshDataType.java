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

package org.apache.eventmesh.connector.jdbc.type.eventmesh;

import org.apache.eventmesh.connector.jdbc.table.type.SQLType;
import org.apache.eventmesh.connector.jdbc.type.AbstractType;

import java.util.Arrays;
import java.util.List;

public class Int64EventMeshDataType extends AbstractType<Long> {

    public static final Int64EventMeshDataType INSTANCE = new Int64EventMeshDataType();

    private Int64EventMeshDataType() {
        super(Long.class, SQLType.BIGINT, "INT64");
    }

    @Override
    public List<String> ofRegistrationKeys() {
        return Arrays.asList(getName(), "int64", "long", "Long", "LONG", "bigint", "BIGINT");
    }

}
