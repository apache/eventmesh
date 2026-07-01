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

import org.apache.eventmesh.connector.jdbc.type.eventmesh.BooleanEventMeshDataType;
import org.apache.eventmesh.connector.jdbc.type.eventmesh.BytesEventMeshDataType;
import org.apache.eventmesh.connector.jdbc.type.eventmesh.DateEventMeshDataType;
import org.apache.eventmesh.connector.jdbc.type.eventmesh.DateTimeEventMeshDataType;
import org.apache.eventmesh.connector.jdbc.type.eventmesh.DecimalEventMeshDataType;
import org.apache.eventmesh.connector.jdbc.type.eventmesh.Float32EventMeshDataType;
import org.apache.eventmesh.connector.jdbc.type.eventmesh.Float64EventMeshDataType;
import org.apache.eventmesh.connector.jdbc.type.eventmesh.Int16EventMeshDataType;
import org.apache.eventmesh.connector.jdbc.type.eventmesh.Int32EventMeshDataType;
import org.apache.eventmesh.connector.jdbc.type.eventmesh.Int64EventMeshDataType;
import org.apache.eventmesh.connector.jdbc.type.eventmesh.Int8EventMeshDataType;
import org.apache.eventmesh.connector.jdbc.type.eventmesh.StringEventMeshDataType;
import org.apache.eventmesh.connector.jdbc.type.eventmesh.TimeEventMeshDataType;

import java.util.HashMap;
import java.util.Map;

public final class EventMeshTypeNameConverter {

    private static Map<String, EventMeshDataType> PRIMITIVE_TYPE_MAP = new HashMap<>(32);

    static {
        PRIMITIVE_TYPE_MAP.put(BooleanEventMeshDataType.INSTANCE.getName(), BooleanEventMeshDataType.INSTANCE);
        PRIMITIVE_TYPE_MAP.put(Float32EventMeshDataType.INSTANCE.getName(), Float32EventMeshDataType.INSTANCE);
        PRIMITIVE_TYPE_MAP.put(Float64EventMeshDataType.INSTANCE.getName(), Float64EventMeshDataType.INSTANCE);
        PRIMITIVE_TYPE_MAP.put(Int8EventMeshDataType.INSTANCE.getName(), Int8EventMeshDataType.INSTANCE);
        PRIMITIVE_TYPE_MAP.put(Int16EventMeshDataType.INSTANCE.getName(), Int16EventMeshDataType.INSTANCE);
        PRIMITIVE_TYPE_MAP.put(Int32EventMeshDataType.INSTANCE.getName(), Int32EventMeshDataType.INSTANCE);
        PRIMITIVE_TYPE_MAP.put(Int64EventMeshDataType.INSTANCE.getName(), Int64EventMeshDataType.INSTANCE);
        PRIMITIVE_TYPE_MAP.put(StringEventMeshDataType.INSTANCE.getName(), StringEventMeshDataType.INSTANCE);
        PRIMITIVE_TYPE_MAP.put(BytesEventMeshDataType.INSTANCE.getName(), BytesEventMeshDataType.INSTANCE);
        PRIMITIVE_TYPE_MAP.put(DateEventMeshDataType.INSTANCE.getName(), DateEventMeshDataType.INSTANCE);
        PRIMITIVE_TYPE_MAP.put(TimeEventMeshDataType.INSTANCE.getName(), TimeEventMeshDataType.INSTANCE);
        PRIMITIVE_TYPE_MAP.put(DateTimeEventMeshDataType.INSTANCE.getName(), DateTimeEventMeshDataType.INSTANCE);
        PRIMITIVE_TYPE_MAP.put(DecimalEventMeshDataType.INSTANCE.getName(), DecimalEventMeshDataType.INSTANCE);
    }

    public static EventMeshDataType ofEventMeshDataType(String dataType) {
        return PRIMITIVE_TYPE_MAP.get(dataType);
    }

}
