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
package cn.webank.defibus.common.protocol;

import com.google.common.base.Preconditions;

public class DeFiBusTopicConfig {
    public static final long DEFAULT_QUEUE_LENGTH = 500;
    private static final String SEPARATOR = " ";
    private String topicName;
    private long maxQueueDepth = DEFAULT_QUEUE_LENGTH;

    public DeFiBusTopicConfig() {
    }

    public DeFiBusTopicConfig(String topicName) {
        this(topicName, DEFAULT_QUEUE_LENGTH);
    }

    public DeFiBusTopicConfig(String topicName, long maxQueueDepth) {
        Preconditions.checkArgument(topicName.indexOf(SEPARATOR) < 0, "topicName is invalid:" + topicName);
        this.topicName = topicName;
        this.maxQueueDepth = maxQueueDepth;
    }

    public String encode() {
        StringBuilder sb = new StringBuilder();

        sb.append(this.topicName);
        sb.append(SEPARATOR);
        sb.append(this.maxQueueDepth);

        return sb.toString();
    }

    public boolean decode(final String in) {
        String[] strs = in.split(SEPARATOR);
        if (strs != null && strs.length == 2) {
            this.topicName = strs[0];

            this.maxQueueDepth = Long.parseLong(strs[1]);

            return true;
        }

        return false;
    }

    public String getTopicName() {
        return topicName;
    }

    public void setTopicName(String topicName) {
        Preconditions.checkArgument(topicName.indexOf(SEPARATOR) < 0, "topicName is invalid:" + topicName);
        this.topicName = topicName;
    }

    public long getMaxQueueDepth() {
        return maxQueueDepth;
    }

    public void setMaxQueueDepth(long maxQueueDepth) {
        this.maxQueueDepth = maxQueueDepth;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        final DeFiBusTopicConfig that = (DeFiBusTopicConfig) o;

        if (maxQueueDepth != that.maxQueueDepth)
            return false;
        if (topicName != null ? !topicName.equals(that.topicName) : that.topicName != null)
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "DeFiBusTopicConfig[" +
            "topicName='" + topicName + '\'' +
            ", maxQueueDepth=" + maxQueueDepth +
            ']';
    }

    @Override
    public int hashCode() {
        int result = topicName.hashCode();
        result = 31 * result + (int) (maxQueueDepth ^ (maxQueueDepth >>> 32));
        return result;
    }
}
