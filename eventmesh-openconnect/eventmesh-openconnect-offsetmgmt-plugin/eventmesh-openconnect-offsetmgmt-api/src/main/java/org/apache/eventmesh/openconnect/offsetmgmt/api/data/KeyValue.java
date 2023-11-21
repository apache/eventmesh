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

package org.apache.eventmesh.openconnect.offsetmgmt.api.data;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.Set;

/**
 * used for connector-record extension
 */
public interface KeyValue {

    KeyValue put(String key, Boolean value);

    KeyValue put(String key, Number value);

    KeyValue put(String key, byte[] value);

    KeyValue put(String key, String value);

    KeyValue put(String key, URI value);

    KeyValue put(String key, OffsetDateTime value);

    KeyValue put(String key, Object value);

    boolean getBoolean(String key);

    boolean getBoolean(String key, boolean defaultValue);

    byte getByte(String key);

    byte getByte(String key, byte defaultValue);

    short getShort(String key);

    short getShort(String key, short defaultValue);

    int getInt(String key);

    int getInt(String key, int defaultValue);

    long getLong(String key);

    long getLong(String key, long defaultValue);

    float getFloat(String key);

    float getFloat(String key, float defaultValue);

    double getDouble(String key);

    double getDouble(String key, double defaultValue);

    byte[] getBytes(String key);

    byte[] getBytes(String key, byte[] defaultValue);

    String getString(String key);

    String getString(String key, String defaultValue);

    URI getURI(String key);

    URI getURI(String key, URI defaultValue);

    OffsetDateTime getOffsetDateTime(String key);

    OffsetDateTime getOffsetDateTime(String key, OffsetDateTime defaultValue);

    Object getObject(String key);

    Object getObject(String key, Object defaultValue);

    <T> T getObject(String key, Class<T> c);

    <T> T getObject(String key, T defaultValue, Class<T> c);

    Set<String> keySet();

    boolean containsKey(String key);
}