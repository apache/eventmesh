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

import java.math.BigDecimal;
import java.util.Objects;

import lombok.Getter;

public class DecimalType extends PrimitiveType<BigDecimal> {

    @Getter
    private final int scale;

    @Getter
    private final int precision;

    public DecimalType(int scale, int precision) {
        super(BigDecimal.class, SQLType.DECIMAL);
        this.precision = precision;
        this.scale = scale;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DecimalType)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        DecimalType that = (DecimalType) o;
        return precision == that.precision && scale == that.scale;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), precision, scale);
    }

    @Override
    public String toString() {
        return getTypeClass().getName();
    }
}
