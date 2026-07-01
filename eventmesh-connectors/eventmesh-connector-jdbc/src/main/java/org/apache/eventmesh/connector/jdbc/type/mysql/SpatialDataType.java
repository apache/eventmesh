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

import org.apache.eventmesh.connector.jdbc.table.type.SQLType;
import org.apache.eventmesh.connector.jdbc.type.AbstractType;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Base64;

import org.hibernate.query.Query;

/**
 * The SpatialDataType class is an abstract class that extends the AbstractType class. It represents a spatial data type for storing byte arrays.
 *
 * <p>
 * The SpatialDataType class provides constructors for specifying the type class, SQL type, and name of the spatial data type.
 * </p>
 * <a href="https://dev.mysql.com/doc/refman/8.0/en/gis-wkb-functions.html">mysql gis-wkb-functions</a>
 */
public abstract class SpatialDataType extends AbstractType<byte[]> {

    public SpatialDataType(String name) {
        super(byte[].class, SQLType.BINARY, name);
    }

    @Override
    public int bindValue(int startIndex, Object value, Query<?> query) {

        if (value == null) {
            query.setParameter(startIndex, null);
            return 1;
        }

        //doc:https://dev.mysql.com/doc/refman/8.0/en/gis-wkb-functions.html
        //Different data types have different methods. For example,
        // the `Point` type uses a method like this: `ST_PointFromWKB(wkb [, srid [, options]])`.
        //Special handling is required when binding data like this.
        if (value instanceof byte[]) {

            ByteBuffer buf = ByteBuffer.wrap((byte[]) value);
            buf.order(ByteOrder.LITTLE_ENDIAN);
            // The first 4 bytes represent the SRID.
            Integer srid = buf.getInt();
            // The remainder is the WKB (Well-Known Binary) data.
            byte[] wkb = new byte[buf.remaining()];
            buf.get(wkb);
            query.setParameter(startIndex, wkb);
            query.setParameter(startIndex + 1, srid);
            return 2;
        }

        throw new RuntimeException();
    }

    @Override
    public Object convert2DatabaseTypeValue(Object value) {
        // Jackson default serialize byte[] as base64
        String strValue = (String) value;
        if (strValue == null) {
            return null;
        }
        return Base64.getDecoder().decode(strValue);
    }
}
