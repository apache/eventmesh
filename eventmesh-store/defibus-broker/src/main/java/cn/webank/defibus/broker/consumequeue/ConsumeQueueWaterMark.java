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

package cn.webank.defibus.broker.consumequeue;

public class ConsumeQueueWaterMark {
    private String consumerGroup;
    private final String topic;
    private final int queueId;
    private long lastDeliverOffset;
    private long accumulated;

    public ConsumeQueueWaterMark(String consumerGroup, String topic, int queueId, long lastDeliverOffset,
        long accumulated) {
        this.consumerGroup = consumerGroup;
        this.topic = topic;
        this.queueId = queueId;
        this.lastDeliverOffset = lastDeliverOffset;
        this.accumulated = accumulated;
    }

    public ConsumeQueueWaterMark(String consumerGroup, String topic, int queueId) {
        this.consumerGroup = consumerGroup;
        this.topic = topic;
        this.queueId = queueId;
    }

    public ConsumeQueueWaterMark(String topic, int queueId) {
        this.topic = topic;
        this.queueId = queueId;
    }

    public void setLastDeliverOffset(long lastDeliverOffset) {
        this.lastDeliverOffset = lastDeliverOffset;
    }

    public void setAccumulated(long accumulated) {
        this.accumulated = accumulated;
    }

    public long getAccumulated() {
        return accumulated;
    }

    public String getConsumerGroup() {
        return consumerGroup;
    }

    public String getTopic() {
        return topic;
    }

    public int getQueueId() {
        return queueId;
    }

    public long getLastDeliverOffset() {
        return lastDeliverOffset;
    }

    @Override
    public String toString() {
        return "ConsumeQueueWaterMark{" +
            "consumerGroup='" + consumerGroup + '\'' +
            ", topic='" + topic + '\'' +
            ", queueId=" + queueId +
            ", lastDeliverOffset=" + lastDeliverOffset +
            ", accumulated=" + accumulated +
            '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof ConsumeQueueWaterMark))
            return false;

        ConsumeQueueWaterMark that = (ConsumeQueueWaterMark) o;

        if (queueId != that.queueId)
            return false;
        if (lastDeliverOffset != that.lastDeliverOffset)
            return false;
        if (accumulated != that.accumulated)
            return false;
        if (!consumerGroup.equals(that.consumerGroup))
            return false;
        return topic.equals(that.topic);
    }

    @Override
    public int hashCode() {
        int result = consumerGroup.hashCode();
        result = 31 * result + topic.hashCode();
        result = 31 * result + queueId;
        result = 31 * result + (int) (lastDeliverOffset ^ (lastDeliverOffset >>> 32));
        result = 31 * result + (int) (accumulated ^ (accumulated >>> 32));
        return result;
    }
}
