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

package org.apache.eventmesh.connector.rabbitmq.utils;

import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.utils.JsonUtils;

import java.io.IOException;
import java.util.Optional;

import com.fasterxml.jackson.core.type.TypeReference;

@SuppressWarnings("all")
public class ByteArrayUtils {

    public static <T> Optional<byte[]> objectToBytes(T obj) throws IOException {
        String s = JsonUtils.toJSONString(obj);
        byte[] bytes = s.getBytes(Constants.DEFAULT_CHARSET);
        return Optional.ofNullable(bytes);
    }

    public static <T> Optional<T> bytesToObject(byte[] bytes) throws IOException, ClassNotFoundException {
        T t = JsonUtils.parseTypeReferenceObject(new String(bytes, Constants.DEFAULT_CHARSET), new TypeReference<T>() {
        });
        return Optional.ofNullable(t);
    }
}
