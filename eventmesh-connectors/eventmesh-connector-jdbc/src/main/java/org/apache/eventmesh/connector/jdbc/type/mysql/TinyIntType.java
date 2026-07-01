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

import org.apache.eventmesh.connector.jdbc.table.catalog.Column;
import org.apache.eventmesh.connector.jdbc.table.type.SQLType;
import org.apache.eventmesh.connector.jdbc.type.AbstractType;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class TinyIntType extends AbstractType<Byte> {

    public static final TinyIntType INSTANCE = new TinyIntType();

    public TinyIntType() {
        super(Byte.class, SQLType.TINYINT, "TINYINT");
    }

    @Override
    public List<String> ofRegistrationKeys() {
        return Arrays.asList(getName(), "tinyint");
    }

    @Override
    public String getTypeName(Column<?> column) {

        final int size = Optional.ofNullable(column.getColumnLength()).orElse(0L).intValue();
        if (size > 0) {
            return String.format("tinyint(%d)", size);
        }
        return "tinyint";
    }
}
