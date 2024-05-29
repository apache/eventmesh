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

package org.apache.eventmesh.common.remote.offset.rocketmq;

import lombok.Data;
import lombok.ToString;
import org.apache.eventmesh.common.remote.offset.RecordPartition;

import java.util.Objects;


@Data
@ToString
public class RocketMQRecordPartition extends RecordPartition {

    /**
     *  key=topic,value=topicName
     *  key=brokerName,value=brokerName
     *  key=queueId,value=queueId
     */

    private String broker;

    private String topic;

    private String queueId;


    @Override
    public Class<? extends RecordPartition> getRecordPartitionClass() {
        return RocketMQRecordPartition.class;
    }

    public RocketMQRecordPartition() {
        super();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        RocketMQRecordPartition that = (RocketMQRecordPartition) o;
        return Objects.equals(broker, that.broker) && Objects.equals(topic, that.topic) && Objects.equals(queueId,
            that.queueId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(broker, topic, queueId);
    }
}
