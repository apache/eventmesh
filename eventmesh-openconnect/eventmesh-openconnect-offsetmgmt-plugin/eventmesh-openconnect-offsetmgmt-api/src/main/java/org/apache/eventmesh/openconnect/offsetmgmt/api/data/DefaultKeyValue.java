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

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class DefaultKeyValue implements KeyValue {

    private Map<String, Object> properties = new ConcurrentHashMap<>();

    @Override
    public boolean getBoolean(String key) {
        if (!properties.containsKey(key)) {
            return false;
        }
        return Boolean.parseBoolean(String.valueOf(properties.get(key)));
    }

    @Override
    public boolean getBoolean(String key, boolean defaultValue) {
        return properties.containsKey(key) ? getBoolean(key) : defaultValue;
    }

    @Override
    public short getShort(String key) {
        if (!properties.containsKey(key)) {
            return 0;
        }
        return Short.parseShort(String.valueOf(properties.get(key)));
    }

    @Override
    public short getShort(String key, short defaultValue) {
        return properties.containsKey(key) ? getShort(key) : defaultValue;
    }

    @Override
    public KeyValue put(String key, boolean value) {
        properties.put(key, String.valueOf(value));
        return this;
    }

    @Override
    public KeyValue put(String key, short value) {
        properties.put(key, String.valueOf(value));
        return this;
    }

    public DefaultKeyValue() {
        properties = new ConcurrentHashMap<String, Object>();
    }

    @Override
    public KeyValue put(String key, int value) {
        properties.put(key, String.valueOf(value));
        return this;
    }

    @Override
    public KeyValue put(String key, long value) {
        properties.put(key, String.valueOf(value));
        return this;
    }

    @Override
    public KeyValue put(String key, double value) {
        properties.put(key, String.valueOf(value));
        return this;
    }

    @Override
    public KeyValue put(String key, Object value) {
        properties.put(key, value);
        return this;
    }

    @Override
    public int getInt(String key) {
        if (!properties.containsKey(key)) {
            return 0;
        }
        return Integer.parseInt(String.valueOf(properties.get(key)));
    }

    @Override
    public int getInt(final String key, final int defaultValue) {
        return properties.containsKey(key) ? getInt(key) : defaultValue;
    }

    @Override
    public long getLong(String key) {
        if (!properties.containsKey(key)) {
            return 0;
        }
        return Long.parseLong(String.valueOf(properties.get(key)));
    }

    @Override
    public long getLong(final String key, final long defaultValue) {
        return properties.containsKey(key) ? getLong(key) : defaultValue;
    }

    @Override
    public double getDouble(String key) {
        if (!properties.containsKey(key)) {
            return 0;
        }
        return Double.parseDouble(String.valueOf(properties.get(key)));
    }

    @Override
    public double getDouble(final String key, final double defaultValue) {
        return properties.containsKey(key) ? getDouble(key) : defaultValue;
    }

    @Override
    public Object getObject(String key) {
        return properties.get(key);
    }

    @Override
    public String getString(String key) {
        return String.valueOf(properties.get(key));
    }

    @Override
    public String getString(final String key, final String defaultValue) {
        return properties.containsKey(key) ? getString(key) : defaultValue;
    }

    @Override
    public Set<String> keySet() {
        return properties.keySet();
    }

    @Override
    public boolean containsKey(String key) {
        return properties.containsKey(key);
    }
}
