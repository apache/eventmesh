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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class DefaultKeyValue implements KeyValue {

    private final Map<String, Object> properties;

    public DefaultKeyValue() {
        properties = new ConcurrentHashMap<>();
    }

    @Override
    public KeyValue put(String key, Boolean value) {
        properties.put(key, value);
        return this;
    }

    @Override
    public KeyValue put(String key, Number value) {
        properties.put(key, value);
        return this;

    }

    @Override
    public KeyValue put(String key, byte[] value) {
        properties.put(key, value);
        return this;
    }

    @Override
    public KeyValue put(String key, String value) {
        properties.put(key, value);
        return this;
    }

    @Override
    public KeyValue put(String key, URI value) {
        properties.put(key, value);
        return this;
    }

    @Override
    public KeyValue put(String key, OffsetDateTime value) {
        properties.put(key, value);
        return this;
    }

    @Override
    public KeyValue put(String key, Object value) {
        properties.put(key, value);
        return this;
    }

    @Override
    public boolean getBoolean(String key) {
        if (!properties.containsKey(key)) {
            return false;
        }
        Object val = properties.get(key);
        if (val instanceof Boolean) {
            return (Boolean) val;
        }
        return false;
    }

    @Override
    public boolean getBoolean(String key, boolean defaultValue) {
        return properties.containsKey(key) ? getBoolean(key) : defaultValue;
    }

    @Override
    public byte getByte(String key) {
        if (!properties.containsKey(key)) {
            return 0;
        }
        Object val = properties.get(key);
        if (val instanceof Byte) {
            return (Byte) val;
        }
        return 0;
    }

    @Override
    public byte getByte(String key, byte defaultValue) {
        return properties.containsKey(key) ? getByte(key) : defaultValue;

    }

    @Override
    public short getShort(String key) {
        if (!properties.containsKey(key)) {
            return 0;
        }
        Object val = properties.get(key);
        if (val instanceof Short) {
            return (Short) val;
        }
        return 0;
    }

    @Override
    public short getShort(String key, short defaultValue) {
        return properties.containsKey(key) ? getShort(key) : defaultValue;
    }

    @Override
    public int getInt(String key) {
        if (!properties.containsKey(key)) {
            return 0;
        }
        Object val = properties.get(key);
        if (val instanceof Integer) {
            return (Integer) val;
        }
        return 0;
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
        Object val = properties.get(key);
        if (val instanceof Long) {
            return (Long) val;
        }
        return 0;
    }

    @Override
    public long getLong(final String key, final long defaultValue) {
        return properties.containsKey(key) ? getLong(key) : defaultValue;
    }

    @Override
    public float getFloat(String key) {
        if (!properties.containsKey(key)) {
            return 0;
        }
        Object val = properties.get(key);
        if (val instanceof Float) {
            return (Float) val;
        }
        return 0;
    }

    @Override
    public float getFloat(String key, float defaultValue) {
        return properties.containsKey(key) ? getFloat(key) : defaultValue;
    }

    @Override
    public double getDouble(String key) {
        if (!properties.containsKey(key)) {
            return 0;
        }
        Object val = properties.get(key);
        if (val instanceof Double) {
            return (Double) val;
        }
        return 0;
    }

    @Override
    public double getDouble(final String key, final double defaultValue) {
        return properties.containsKey(key) ? getDouble(key) : defaultValue;
    }

    @Override
    public byte[] getBytes(String key) {
        if (!properties.containsKey(key)) {
            return new byte[]{};
        }
        Object val = properties.get(key);
        if (val instanceof byte[]) {
            return (byte[]) val;
        }
        return new byte[]{};
    }

    @Override
    public byte[] getBytes(String key, byte[] defaultValue) {
        return properties.containsKey(key) ? getBytes(key) : defaultValue;
    }

    @Override
    public String getString(String key) {
        if (!properties.containsKey(key)) {
            return "";
        }
        Object val = properties.get(key);
        if (val instanceof String) {
            return (String) val;
        }
        return "";
    }

    @Override
    public String getString(final String key, final String defaultValue) {
        return properties.containsKey(key) ? getString(key) : defaultValue;
    }

    @Override
    public URI getURI(String key) {
        if (!properties.containsKey(key)) {
            return null;
        }
        Object val = properties.get(key);
        if (val instanceof URI) {
            return (URI) val;
        }
        return null;
    }

    @Override
    public URI getURI(String key, URI defaultValue) {
        return properties.containsKey(key) ? getURI(key) : defaultValue;
    }

    @Override
    public OffsetDateTime getOffsetDateTime(String key) {
        if (!properties.containsKey(key)) {
            return null;
        }
        Object val = properties.get(key);
        if (val instanceof OffsetDateTime) {
            return (OffsetDateTime) val;
        }
        return null;
    }

    @Override
    public OffsetDateTime getOffsetDateTime(String key, OffsetDateTime defaultValue) {
        return properties.containsKey(key) ? getOffsetDateTime(key) : defaultValue;
    }

    @Override
    public Object getObject(String key) {
        return properties.getOrDefault(key, null);
    }

    @Override
    public Object getObject(String key, Object defaultValue) {
        return properties.getOrDefault(key, defaultValue);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getObject(String key, Class<T> c) {
        if (!properties.containsKey(key)) {
            return null;
        }
        Object val = properties.get(key);
        if (val.getClass() == c) {
            return (T) val;
        }
        return null;
    }

    @Override
    public <T> T getObject(String key, T defaultValue, Class<T> c) {
        return properties.containsKey(key) ? getObject(key, c) : defaultValue;
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
