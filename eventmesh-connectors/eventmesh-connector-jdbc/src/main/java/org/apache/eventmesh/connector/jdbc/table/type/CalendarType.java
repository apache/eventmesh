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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.Temporal;
import java.util.Objects;


public class CalendarType<T extends Temporal> implements EventMeshDataType<T> {

    // Constants for LocalDate, LocalTime, and LocalDateTime types
    public static final CalendarType<LocalDate> LOCAL_DATE_TYPE = new CalendarType<>(LocalDate.class, SQLType.DATE);

    public static final CalendarType<LocalTime> LOCAL_TIME_TYPE = new CalendarType<>(LocalTime.class, SQLType.TIME);

    public static final CalendarType<LocalDateTime> LOCAL_DATE_TIME_TYPE = new CalendarType<>(LocalDateTime.class, SQLType.TIMESTAMP);

    private final Class<T> typeClass;
    private final SQLType sqlType;

    private CalendarType(Class<T> typeClass, SQLType sqlType) {
        this.typeClass = typeClass;
        this.sqlType = sqlType;
    }

    /**
     * Returns the type class of the data.
     *
     * @return the type class of the data.
     */
    @Override
    public Class<T> getTypeClass() {
        return typeClass;
    }

    /**
     * Returns the SQL type of the data.
     *
     * @return the SQL type of the data.
     */
    @Override
    public SQLType getSQLType() {
        return sqlType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CalendarType)) {
            return false;
        }
        CalendarType<?> that = (CalendarType<?>) o;
        return Objects.equals(getTypeClass(), that.getTypeClass()) && sqlType == that.sqlType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(getTypeClass(), sqlType);
    }

    @Override
    public String toString() {
        return typeClass.getName();
    }
}
