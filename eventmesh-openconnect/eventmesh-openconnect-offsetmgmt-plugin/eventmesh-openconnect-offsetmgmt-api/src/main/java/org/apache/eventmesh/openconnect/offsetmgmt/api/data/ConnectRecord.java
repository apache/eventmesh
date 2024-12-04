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

import org.apache.eventmesh.common.remote.offset.RecordOffset;
import org.apache.eventmesh.common.remote.offset.RecordPartition;
import org.apache.eventmesh.common.remote.offset.RecordPosition;
import org.apache.eventmesh.openconnect.offsetmgmt.api.callback.SendMessageCallback;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import lombok.Getter;
import lombok.Setter;

/**
 * SourceDataEntries are generated by SourceTasks and passed to specific message queue to store.
 */
@Getter
public class ConnectRecord {

    private final String recordId = UUID.randomUUID().toString();

    @Setter
    private Long timestamp;

    @Setter
    private Object data;

    @Setter
    private RecordPosition position;

    @Setter
    private KeyValue extensions;

    @Setter
    private SendMessageCallback callback;

    public ConnectRecord() {

    }

    public ConnectRecord(RecordPartition recordPartition, RecordOffset recordOffset,
        Long timestamp) {
        this(recordPartition, recordOffset, timestamp, null);
    }

    public ConnectRecord(RecordPartition recordPartition, RecordOffset recordOffset,
        Long timestamp, Object data) {
        if (recordPartition == null || recordOffset == null) {
            this.position = null;
        } else {
            this.position = new RecordPosition(recordPartition, recordOffset);
        }
        this.timestamp = timestamp;
        this.data = data;
    }

    public void addExtension(KeyValue extensions) {
        if (this.extensions == null) {
            this.extensions = new DefaultKeyValue();
        }
        Set<String> keySet = extensions.keySet();
        for (String key : keySet) {
            this.extensions.put(key, extensions.getObject(key));
        }
    }

    public void addExtension(String key, Object value) {
        if (this.extensions == null) {
            this.extensions = new DefaultKeyValue();
        }
        this.extensions.put(key, value);
    }

    public String getExtension(String key) {
        if (this.extensions == null || !extensions.containsKey(key)) {
            return null;
        }
        return this.extensions.getString(key);
    }

    public <T> T getExtension(String key, Class<T> c) {
        if (this.extensions == null) {
            return null;
        }
        return this.extensions.getObject(key, c);
    }

    public Object getExtensionObj(String key) {
        if (this.extensions == null) {
            return null;
        }
        return this.extensions.getObject(key);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ConnectRecord)) {
            return false;
        }
        ConnectRecord that = (ConnectRecord) o;
        return Objects.equals(recordId, that.recordId) && Objects.equals(timestamp, that.timestamp) && Objects.equals(data, that.data)
            && Objects.equals(position, that.position) && Objects.equals(extensions, that.extensions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(recordId, timestamp, data, position, extensions);
    }

    @Override
    public String toString() {
        return "ConnectRecord{"
            + "recordId=" + recordId
            + ", timestamp=" + timestamp
            + ", data=" + data
            + ", position=" + position
            + ", extensions=" + extensions
            + "}";
    }
}
