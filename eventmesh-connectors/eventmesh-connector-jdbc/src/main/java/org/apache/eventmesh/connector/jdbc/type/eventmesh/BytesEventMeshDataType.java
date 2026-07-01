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
import java.util.Base64;
import java.util.List;

public class BytesEventMeshDataType extends AbstractType<byte[]> {

    public static final BytesEventMeshDataType INSTANCE = new BytesEventMeshDataType();

    public BytesEventMeshDataType() {
        super(byte[].class, SQLType.BINARY, "BYTES");
    }

    @Override
    public List<String> ofRegistrationKeys() {
        return Arrays.asList("bytes", getName());
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
