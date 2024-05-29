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

package org.apache.eventmesh.common.remote.offset;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.apache.eventmesh.common.remote.offset.S3.S3RecordOffset;
import org.apache.eventmesh.common.remote.offset.S3.S3RecordPartition;
import org.apache.eventmesh.common.remote.offset.canal.CanalRecordOffset;
import org.apache.eventmesh.common.remote.offset.canal.CanalRecordPartition;
import org.apache.eventmesh.common.remote.offset.file.FileRecordOffset;
import org.apache.eventmesh.common.remote.offset.file.FileRecordPartition;
import org.apache.eventmesh.common.remote.offset.kafka.KafkaRecordOffset;
import org.apache.eventmesh.common.remote.offset.kafka.KafkaRecordPartition;
import org.apache.eventmesh.common.remote.offset.pulsar.PulsarRecordOffset;
import org.apache.eventmesh.common.remote.offset.pulsar.PulsarRecordPartition;
import org.apache.eventmesh.common.remote.offset.rocketmq.RocketMQRecordOffset;
import org.apache.eventmesh.common.remote.offset.rocketmq.RocketMQRecordPartition;

import java.util.Objects;

public class RecordPosition {
    @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
    @JsonSubTypes({
            @JsonSubTypes.Type(value = CanalRecordPartition.class, name = "CanalRecordPartition"),
            @JsonSubTypes.Type(value = FileRecordPartition.class, name = "FileRecordPartition"),
            @JsonSubTypes.Type(value = S3RecordPartition.class, name = "S3RecordPartition"),
            @JsonSubTypes.Type(value = KafkaRecordPartition.class, name = "KafkaRecordPartition"),
            @JsonSubTypes.Type(value = PulsarRecordPartition.class, name = "PulsarRecordPartition"),
            @JsonSubTypes.Type(value = RocketMQRecordPartition.class, name = "RocketMQRecordPartition"),
    })
    private RecordPartition recordPartition;

    private Class<? extends RecordPartition> recordPartitionClazz;

    @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
    @JsonSubTypes({
            @JsonSubTypes.Type(value = CanalRecordOffset.class, name = "CanalRecordOffset"),
            @JsonSubTypes.Type(value = FileRecordOffset.class, name = "FileRecordOffset"),
            @JsonSubTypes.Type(value = S3RecordOffset.class, name = "S3RecordOffset"),
            @JsonSubTypes.Type(value = KafkaRecordOffset.class, name = "KafkaRecordOffset"),
            @JsonSubTypes.Type(value = PulsarRecordOffset.class, name = "PulsarRecordOffset"),
            @JsonSubTypes.Type(value = RocketMQRecordOffset.class, name = "RocketMQRecordOffset"),
    })
    private RecordOffset recordOffset;

    private Class<? extends RecordOffset> recordOffsetClazz;

    public RecordPosition() {

    }

    public RecordPosition(
            RecordPartition recordPartition, RecordOffset recordOffset) {
        this.recordPartition = recordPartition;
        this.recordOffset = recordOffset;
        this.recordPartitionClazz = recordPartition.getRecordPartitionClass();
        this.recordOffsetClazz = recordOffset.getRecordOffsetClass();
    }

    public void setRecordPartition(RecordPartition recordPartition) {
        this.recordPartition = recordPartition;
        if (recordPartition == null) {
            this.recordPartitionClazz = null;
            return;
        }
        this.recordPartitionClazz = recordPartition.getRecordPartitionClass();
    }

    public void setRecordOffset(RecordOffset recordOffset) {
        this.recordOffset = recordOffset;
        if (recordOffset == null) {
            this.recordOffsetClazz = null;
            return;
        }
        this.recordOffsetClazz = recordOffset.getRecordOffsetClass();
    }

    public RecordPartition getRecordPartition() {
        return recordPartition;
    }

    public RecordOffset getRecordOffset() {
        return recordOffset;
    }

    public Class<? extends RecordPartition> getRecordPartitionClazz() {
        return recordPartitionClazz;
    }

    public Class<? extends RecordOffset> getRecordOffsetClazz() {
        return recordOffsetClazz;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RecordPosition)) {
            return false;
        }
        RecordPosition position = (RecordPosition) o;
        return recordPartition.equals(position.recordPartition) && recordOffset.equals(position.recordOffset);
    }

    @Override
    public int hashCode() {
        return Objects.hash(recordPartition, recordOffset);
    }
}
